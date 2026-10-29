//! Shared suspended Windows worker launch: bounded Job, isolated env, exact owned cleanup.
#![cfg(windows)]
use std::{
    mem::size_of,
    os::windows::{io::AsRawHandle, process::CommandExt},
    process::{Child, Command, Stdio},
};
use windows_sys::Win32::{
    Foundation::{CloseHandle, HANDLE, INVALID_HANDLE_VALUE},
    System::{
        Diagnostics::ToolHelp::{
            CreateToolhelp32Snapshot, Thread32First, Thread32Next, TH32CS_SNAPTHREAD, THREADENTRY32,
        },
        JobObjects::{
            AssignProcessToJobObject, CreateJobObjectW, JobObjectExtendedLimitInformation,
            QueryInformationJobObject, SetInformationJobObject,
            JOBOBJECT_EXTENDED_LIMIT_INFORMATION, JOB_OBJECT_LIMIT_ACTIVE_PROCESS,
            JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE, JOB_OBJECT_LIMIT_PROCESS_MEMORY,
        },
        Threading::{
            OpenThread, ResumeThread, CREATE_NO_WINDOW, CREATE_SUSPENDED, THREAD_SUSPEND_RESUME,
        },
    },
};

pub(crate) struct Handle(pub(crate) HANDLE);
impl Drop for Handle {
    fn drop(&mut self) {
        unsafe {
            CloseHandle(self.0);
        }
    }
}
pub(crate) struct Owned {
    pub(crate) child: Child,
    pub(crate) job: Handle,
}
impl Drop for Owned {
    fn drop(&mut self) {
        if self.child.try_wait().ok().flatten().is_none() {
            let _ = self.child.kill();
        }
        let _ = self.child.wait();
    }
}

fn attach_and_resume(child: &mut Child, memory: usize) -> Result<Handle, String> {
    unsafe {
        let job = CreateJobObjectW(std::ptr::null(), std::ptr::null());
        if job.is_null() {
            return Err("WORKER_LIMIT_UNAVAILABLE".into());
        }
        let job = Handle(job);
        let mut limits = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            | JOB_OBJECT_LIMIT_PROCESS_MEMORY
            | JOB_OBJECT_LIMIT_ACTIVE_PROCESS;
        limits.BasicLimitInformation.ActiveProcessLimit = 1;
        limits.ProcessMemoryLimit = memory;
        if SetInformationJobObject(
            job.0,
            JobObjectExtendedLimitInformation,
            (&limits as *const _) as _,
            size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>() as u32,
        ) == 0
            || AssignProcessToJobObject(job.0, child.as_raw_handle() as HANDLE) == 0
        {
            return Err("WORKER_LIMIT_UNAVAILABLE".into());
        }
        let snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD, 0);
        if snapshot == INVALID_HANDLE_VALUE {
            return Err("WORKER_START_FAILED".into());
        }
        let snapshot = Handle(snapshot);
        let mut entry = THREADENTRY32 {
            dwSize: size_of::<THREADENTRY32>() as u32,
            ..Default::default()
        };
        let mut found = Vec::new();
        let mut ok = Thread32First(snapshot.0, &mut entry);
        while ok != 0 {
            if entry.th32OwnerProcessID == child.id() {
                found.push(entry.th32ThreadID);
            }
            entry.dwSize = size_of::<THREADENTRY32>() as u32;
            ok = Thread32Next(snapshot.0, &mut entry);
        }
        if found.len() != 1 {
            return Err("WORKER_START_FAILED".into());
        }
        let main = OpenThread(THREAD_SUSPEND_RESUME, 0, found[0]);
        if main.is_null() {
            return Err("WORKER_START_FAILED".into());
        }
        let main = Handle(main);
        if ResumeThread(main.0) != 1 {
            return Err("WORKER_START_FAILED".into());
        }
        Ok(job)
    }
}

pub(crate) fn start(command: &mut Command, memory: usize) -> Result<Owned, String> {
    // Decoder has no business configuration or credentials. Do not inherit provider envs.
    command.env_clear();
    if let Some(system_root) = std::env::var_os("SystemRoot") {
        command.env("SystemRoot", system_root);
    }
    let mut child = command
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .creation_flags(CREATE_SUSPENDED | CREATE_NO_WINDOW)
        .spawn()
        .map_err(|_| "WORKER_START_FAILED")?;
    match attach_and_resume(&mut child, memory) {
        Ok(job) => Ok(Owned { child, job }),
        Err(error) => {
            let _ = child.kill();
            let _ = child.wait();
            Err(error)
        }
    }
}

pub(crate) fn peak(job: &Handle) -> Result<usize, String> {
    let mut information = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
    let ok = unsafe {
        QueryInformationJobObject(
            job.0,
            JobObjectExtendedLimitInformation,
            (&mut information as *mut _) as _,
            size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>() as u32,
            std::ptr::null_mut(),
        )
    };
    if ok == 0 {
        return Err("WORKER_LIMIT_UNAVAILABLE".into());
    }
    Ok(information.PeakProcessMemoryUsed)
}
