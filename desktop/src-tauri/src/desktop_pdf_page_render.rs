//! PDF content is rasterized locally, never loaded as an iframe/plugin or executable document.
#[cfg(target_os = "macos")]
pub fn render_page(bytes: &[u8], page_number: u32) -> Result<Vec<u8>, String> {
    use image::{DynamicImage, ImageFormat, RgbaImage};
    use std::{ffi::c_void, io::Cursor, ptr};
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Point {
        x: f64,
        y: f64,
    }
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Size {
        width: f64,
        height: f64,
    }
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Rect {
        origin: Point,
        size: Size,
    }
    #[repr(C)]
    struct Transform {
        a: f64,
        b: f64,
        c: f64,
        d: f64,
        tx: f64,
        ty: f64,
    }
    type Handle = *mut c_void;
    #[link(name = "CoreGraphics", kind = "framework")]
    unsafe extern "C" {
        fn CGDataProviderCreateWithData(
            info: Handle,
            data: *const c_void,
            size: usize,
            release: Option<unsafe extern "C" fn(Handle, *const c_void, usize)>,
        ) -> Handle;
        fn CGDataProviderRelease(value: Handle);
        fn CGPDFDocumentCreateWithProvider(provider: Handle) -> Handle;
        fn CGPDFDocumentRelease(value: Handle);
        fn CGPDFDocumentGetPage(document: Handle, page: usize) -> Handle;
        fn CGPDFPageGetBoxRect(page: Handle, box_type: i32) -> Rect;
        fn CGPDFPageGetRotationAngle(page: Handle) -> i32;
        fn CGPDFPageGetDrawingTransform(
            page: Handle,
            box_type: i32,
            rect: Rect,
            rotate: i32,
            preserve: bool,
        ) -> Transform;
        fn CGColorSpaceCreateDeviceRGB() -> Handle;
        fn CGColorSpaceRelease(value: Handle);
        fn CGBitmapContextCreate(
            data: Handle,
            width: usize,
            height: usize,
            bits: usize,
            row: usize,
            color: Handle,
            info: u32,
        ) -> Handle;
        fn CGContextRelease(value: Handle);
        fn CGContextSetRGBFillColor(context: Handle, r: f64, g: f64, b: f64, a: f64);
        fn CGContextFillRect(context: Handle, rect: Rect);
        fn CGContextConcatCTM(context: Handle, transform: Transform);
        fn CGContextScaleCTM(context: Handle, x: f64, y: f64);
        fn CGContextDrawPDFPage(context: Handle, page: Handle);
    }
    struct Owned(Handle, unsafe extern "C" fn(Handle));
    impl Drop for Owned {
        fn drop(&mut self) {
            unsafe { (self.1)(self.0) }
        }
    }
    fn owned(handle: Handle, release: unsafe extern "C" fn(Handle)) -> Result<Owned, String> {
        if handle.is_null() {
            Err("本地 PDF 页面无法绘制".into())
        } else {
            Ok(Owned(handle, release))
        }
    }
    // All borrowed bytes and bitmap storage outlive the handles; no paths or callbacks escape.
    unsafe {
        let provider = owned(
            CGDataProviderCreateWithData(ptr::null_mut(), bytes.as_ptr().cast(), bytes.len(), None),
            CGDataProviderRelease,
        )?;
        let document = owned(
            CGPDFDocumentCreateWithProvider(provider.0),
            CGPDFDocumentRelease,
        )?;
        let page = CGPDFDocumentGetPage(document.0, page_number as usize);
        if page.is_null() {
            return Err("本地 PDF 页码不可用".into());
        }
        let bounds = CGPDFPageGetBoxRect(page, 1); // CropBox, falling back to MediaBox in CoreGraphics.
        let (mut width, mut height) = (bounds.size.width, bounds.size.height);
        if !width.is_finite() || !height.is_finite() || width <= 0.0 || height <= 0.0 {
            return Err("本地 PDF 页面尺寸无效".into());
        }
        if CGPDFPageGetRotationAngle(page).rem_euclid(180) != 0 {
            std::mem::swap(&mut width, &mut height);
        }
        let (page_width, page_height) = (width, height);
        let scale = 2048.0 / width.max(height);
        let (width, height) = (
            (width * scale).round().max(1.0) as u32,
            (height * scale).round().max(1.0) as u32,
        );
        let mut pixels = vec![255u8; (width as usize) * (height as usize) * 4];
        let color = owned(CGColorSpaceCreateDeviceRGB(), CGColorSpaceRelease)?;
        let context = owned(
            CGBitmapContextCreate(
                pixels.as_mut_ptr().cast(),
                width as usize,
                height as usize,
                8,
                width as usize * 4,
                color.0,
                1 | (4 << 12),
            ),
            CGContextRelease,
        )?;
        let rect = Rect {
            origin: Point { x: 0.0, y: 0.0 },
            size: Size {
                width: width as f64,
                height: height as f64,
            },
        };
        CGContextSetRGBFillColor(context.0, 1.0, 1.0, 1.0, 1.0);
        CGContextFillRect(context.0, rect);
        CGContextScaleCTM(
            context.0,
            width as f64 / page_width,
            height as f64 / page_height,
        );
        let rect = Rect {
            origin: Point { x: 0.0, y: 0.0 },
            size: Size {
                width: page_width,
                height: page_height,
            },
        };
        CGContextConcatCTM(
            context.0,
            CGPDFPageGetDrawingTransform(page, 1, rect, 0, true),
        );
        CGContextDrawPDFPage(context.0, page);
        drop(context);
        let image = RgbaImage::from_raw(width, height, pixels).ok_or("本地 PDF 页面编码失败")?;
        let mut output = Cursor::new(Vec::new());
        DynamicImage::ImageRgba8(image)
            .write_to(&mut output, ImageFormat::Png)
            .map_err(|_| "本地 PDF 页面编码失败")?;
        Ok(output.into_inner())
    }
}
#[cfg(windows)]
pub fn render_page(bytes: &[u8], page_number: u32) -> Result<Vec<u8>, String> {
    render_page_cancellable(
        bytes,
        page_number,
        &std::sync::atomic::AtomicBool::new(false),
    )
}

#[cfg(windows)]
pub fn render_page_cancellable(
    bytes: &[u8],
    page_number: u32,
    cancelled: &std::sync::atomic::AtomicBool,
) -> Result<Vec<u8>, String> {
    if page_number == 0 {
        return Err("本地 PDF 页码不可用".into());
    }
    if bytes.is_empty() || bytes.len() as u64 > crate::MAX_CONVERSATION_ATTACHMENT_BYTES {
        return Err("本地 PDF 大小超出附件预览限制".into());
    }
    // A fresh MTA avoids changing Tauri's UI/COM apartment. Only verified bytes
    // cross this seam; no file path, network URL or browser document is opened.
    std::thread::scope(|scope| {
        std::thread::Builder::new()
            .name("nf-pdf-page".into())
            .spawn_scoped(scope, || {
                windows_render::render_with_cancellation(
                    bytes,
                    page_number,
                    std::time::Duration::from_secs(30),
                    cancelled,
                )
            })
            .map_err(|_| "Windows PDF 绘制线程不可用".to_string())?
            .join()
            .map_err(|_| "Windows PDF 绘制线程异常".to_string())?
            .map_err(|error| {
                // OS messages may include private details. Keep only a numeric
                // diagnostic code; never put PDF text or paths into errors.
                format!(
                    "Windows 无法绘制该 PDF 页面（代码 {:08X}）；文件可能损坏、加密或超时",
                    error.code().0 as u32
                )
            })
    })
}

#[cfg(windows)]
mod windows_render {
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::time::{Duration, Instant};

    #[cfg(test)]
    thread_local! {
        pub(super) static RENDER_STARTED: std::cell::RefCell<Option<std::sync::mpsc::Sender<bool>>> = const { std::cell::RefCell::new(None) };
    }
    use windows::{
        core::{Error, Interface, Result, HRESULT},
        Data::Pdf::{PdfDocument, PdfPageRenderOptions},
        Foundation::IClosable,
        Graphics::Imaging::BitmapEncoder,
        Storage::Streams::{DataReader, DataWriter, InMemoryRandomAccessStream},
        Win32::System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED},
        UI::Color,
    };

    struct Apartment;
    impl Drop for Apartment {
        fn drop(&mut self) {
            unsafe { RoUninitialize() }
        }
    }
    #[derive(Default)]
    struct Closables(Vec<IClosable>);
    impl Closables {
        fn add<T: Interface>(&mut self, value: &T) -> Result<()> {
            self.0.push(value.cast()?);
            Ok(())
        }
    }
    impl Drop for Closables {
        fn drop(&mut self) {
            for value in self.0.iter().rev() {
                let _ = value.Close();
            }
        }
    }

    #[cfg(test)]
    pub(super) fn render_with_timeout(
        bytes: &[u8],
        page_number: u32,
        timeout: Duration,
    ) -> Result<Vec<u8>> {
        render_with_cancellation(bytes, page_number, timeout, &AtomicBool::new(false))
    }

    pub(super) fn render_with_cancellation(
        bytes: &[u8],
        page_number: u32,
        timeout: Duration,
        cancelled: &AtomicBool,
    ) -> Result<Vec<u8>> {
        unsafe {
            RoInitialize(RO_INIT_MULTITHREADED)?;
        }
        let _apartment = Apartment;
        let mut closables = Closables::default();
        let deadline = Instant::now() + timeout;
        // All WinRT asynchronous work shares one deadline. Cancel only this
        // operation on timeout; never leave an unbounded detached worker.
        macro_rules! finish {
            ($start:expr) => {{
                if cancelled.load(Ordering::SeqCst) {
                    return Err(Error::from_hresult(HRESULT(0x800704C7u32 as i32)));
                }
                let operation = $start?;
                let mut cancellation_requested = false;
                loop {
                    if !cancellation_requested && cancelled.load(Ordering::SeqCst) {
                        let _ = operation.Cancel();
                        cancellation_requested = true;
                    }
                    if Instant::now() >= deadline {
                        let _ = operation.Cancel();
                        return Err(Error::from_hresult(HRESULT(0x800705B4u32 as i32)));
                    }
                    if operation.Status()?.0 != 0 {
                        if cancellation_requested {
                            return Err(Error::from_hresult(HRESULT(0x800704C7u32 as i32)));
                        }
                        break;
                    } // AsyncStatus::Started
                    std::thread::sleep(Duration::from_millis(5));
                }
                operation.GetResults()?
            }};
        }
        let invalid = || Error::from_hresult(HRESULT(0x80070057u32 as i32));
        let input = InMemoryRandomAccessStream::new()?;
        closables.add(&input)?;
        let writer = DataWriter::CreateDataWriter(&input)?;
        closables.add(&writer)?;
        writer.WriteBytes(bytes)?;
        if finish!(writer.StoreAsync()) as usize != bytes.len() {
            return Err(invalid());
        }
        writer.DetachStream()?;
        input.Seek(0)?;
        let document = finish!(PdfDocument::LoadFromStreamAsync(&input));
        if page_number > document.PageCount()? {
            return Err(invalid());
        }
        let page = document.GetPage(page_number - 1)?;
        closables.add(&page)?;
        let size = page.Size()?;
        if !size.Width.is_finite()
            || !size.Height.is_finite()
            || size.Width <= 0.0
            || size.Height <= 0.0
        {
            return Err(invalid());
        }
        let scale = 2048.0 / f64::from(size.Width.max(size.Height));
        let width = (f64::from(size.Width) * scale).round().max(1.0) as u32;
        let height = (f64::from(size.Height) * scale).round().max(1.0) as u32;
        let options = PdfPageRenderOptions::new()?;
        options.SetDestinationWidth(width)?;
        options.SetDestinationHeight(height)?;
        options.SetBackgroundColor(Color {
            A: 255,
            R: 255,
            G: 255,
            B: 255,
        })?;
        options.SetBitmapEncoderId(BitmapEncoder::PngEncoderId()?)?;
        let output = InMemoryRandomAccessStream::new()?;
        closables.add(&output)?;
        finish!({
            let operation = page.RenderWithOptionsToStreamAsync(&output, &options)?;
            #[cfg(test)]
            RENDER_STARTED.with(|hook| {
                if let Some(sender) = hook.borrow_mut().take() {
                    let _ = sender.send(operation.Status().is_ok_and(|status| status.0 == 0));
                }
            });
            Ok::<_, Error>(operation)
        });
        let length = output.Size()?;
        if length == 0 || length > 32 * 1024 * 1024 {
            return Err(invalid());
        }
        let reader = DataReader::CreateDataReader(&output.GetInputStreamAt(0)?)?;
        closables.add(&reader)?;
        if u64::from(finish!(reader.LoadAsync(length as u32))) != length {
            return Err(invalid());
        }
        let mut png = vec![0u8; length as usize];
        reader.ReadBytes(&mut png)?;
        if cancelled.load(Ordering::SeqCst) {
            return Err(Error::from_hresult(HRESULT(0x800704C7u32 as i32)));
        }
        if !png.starts_with(b"\x89PNG\r\n\x1a\n") {
            return Err(invalid());
        }
        Ok(png)
    }
}

#[cfg(not(any(target_os = "macos", windows)))]
pub fn render_page(_bytes: &[u8], _page_number: u32) -> Result<Vec<u8>, String> {
    Err("当前平台尚未提供本地 PDF 页面绘制".into())
}

#[cfg(not(windows))]
pub fn render_page_cancellable(
    bytes: &[u8],
    page_number: u32,
    cancelled: &std::sync::atomic::AtomicBool,
) -> Result<Vec<u8>, String> {
    use std::sync::atomic::Ordering;
    if cancelled.load(Ordering::SeqCst) {
        return Err("PDF 预览已取消".into());
    }
    let result = render_page(bytes, page_number)?;
    if cancelled.load(Ordering::SeqCst) {
        return Err("PDF 预览已取消".into());
    }
    Ok(result)
}

#[cfg(all(test, any(target_os = "macos", windows)))]
mod tests {
    use super::render_page;
    const PDF: &[u8] = include_bytes!("../tests/fixtures/preview-page.pdf");
    #[test]
    fn pdf_page_renders_real_ink_with_bounded_dimensions() {
        let png = render_page(PDF, 1).unwrap();
        let image = image::load_from_memory(&png).unwrap().to_rgba8();
        assert_eq!((image.width(), image.height()), (1229, 2048));
        let dark = image
            .pixels()
            .filter(|p| p.0[0] < 128 && p.0[1] < 128 && p.0[2] < 128)
            .count();
        assert!(dark > 500, "a successful but blank page must fail");
        let top_ink = image
            .enumerate_pixels()
            .filter(|(_, y, p)| *y < image.height() / 4 && p.0[0] < 128)
            .count();
        let bottom_ink = image
            .enumerate_pixels()
            .filter(|(_, y, p)| *y > image.height() * 3 / 4 && p.0[0] < 128)
            .count();
        assert!(
            top_ink > 500 && bottom_ink > 500,
            "page must fill the raster, preserving top and bottom content"
        );
        if let Ok(path) = std::env::var("NANFENG_PDF_TEST_PNG") {
            std::fs::write(path, png).unwrap();
        }
    }
    #[test]
    fn corrupt_documents_and_missing_pages_fail_explicitly() {
        assert!(render_page(b"not a PDF", 1).is_err());
        assert!(render_page(PDF, 0).is_err());
        assert!(render_page(PDF, 2).is_err());
    }

    #[cfg(windows)]
    const MATRIX: &[u8] = include_bytes!("../tests/fixtures/windows-preview-matrix.pdf");

    #[cfg(windows)]
    #[test]
    fn windows_renders_chinese_rotation_scanned_and_last_pages() {
        let expected = [(1229, 2048), (2048, 1229), (2048, 1536), (2048, 512)];
        for (index, dimensions) in expected.into_iter().enumerate() {
            let png = render_page(MATRIX, index as u32 + 1).unwrap();
            let image = image::load_from_memory(&png).unwrap().to_rgb8();
            assert_eq!(image.dimensions(), dimensions, "page {}", index + 1);
            assert!(
                image
                    .pixels()
                    .filter(|p| p.0.iter().any(|v| *v < 200))
                    .count()
                    > 1000
            );
            if index == 2 {
                assert!(image.get_pixel(100, 100).0[0] > 180);
                assert!(image.get_pixel(100, 100).0[1] < 80);
                assert!(image.get_pixel(1900, 100).0[2] > 180);
            }
            if let Ok(directory) = std::env::var("NANFENG_PDF_TEST_DIRECTORY") {
                std::fs::write(
                    std::path::Path::new(&directory).join(format!("page-{}.png", index + 1)),
                    png,
                )
                .unwrap();
            }
        }
    }

    #[cfg(windows)]
    #[test]
    fn windows_rejects_password_protected_and_oversize_pdf() {
        let encrypted = include_bytes!("../tests/fixtures/windows-preview-encrypted.pdf");
        assert!(render_page(encrypted, 1).is_err());
        assert!(render_page(
            &vec![0; crate::MAX_CONVERSATION_ATTACHMENT_BYTES as usize + 1],
            1
        )
        .is_err());
        assert!(render_page(MATRIX, 5).is_err());
    }

    #[cfg(windows)]
    #[test]
    fn windows_timeout_does_not_poison_later_rendering() {
        // Like production, use a fresh apartment on a dedicated thread.
        let error = std::thread::spawn(|| {
            super::windows_render::render_with_timeout(PDF, 1, std::time::Duration::ZERO)
        })
        .join()
        .unwrap()
        .unwrap_err();
        assert_eq!(error.code().0 as u32, 0x800705B4);
        for _ in 0..3 {
            assert!(render_page(PDF, 1).unwrap().starts_with(b"\x89PNG"));
        }
    }

    #[cfg(windows)]
    fn cancel_in_flight() -> std::time::Duration {
        use std::sync::{
            atomic::{AtomicBool, Ordering},
            mpsc, Arc,
        };
        let cancelled = Arc::new(AtomicBool::new(false));
        let token = Arc::clone(&cancelled);
        let (sender, receiver) = mpsc::channel();
        let worker = std::thread::spawn(move || {
            super::windows_render::RENDER_STARTED.with(|hook| *hook.borrow_mut() = Some(sender));
            super::windows_render::render_with_cancellation(
                include_bytes!("../tests/fixtures/windows-preview-cancellation.pdf"),
                1,
                std::time::Duration::from_secs(30),
                &token,
            )
        });
        // Observe the real WinRT operation's Started status, not a sleep-based
        // guess that the worker must have reached the renderer by now.
        let pending = receiver.recv_timeout(std::time::Duration::from_secs(10));
        let started = std::time::Instant::now();
        cancelled.store(true, Ordering::SeqCst);
        let result = worker.join().unwrap();
        assert_eq!(
            pending.unwrap(),
            true,
            "fixture must reach an in-flight system render"
        );
        assert_eq!(result.unwrap_err().code().0 as u32, 0x800704C7);
        let elapsed = started.elapsed();
        assert!(
            elapsed < std::time::Duration::from_secs(5),
            "cancellation took {elapsed:?}"
        );
        elapsed
    }

    #[cfg(windows)]
    #[test]
    fn windows_cancel_during_system_render_then_recover() {
        let elapsed = cancel_in_flight();
        assert!(render_page(PDF, 1).unwrap().starts_with(b"\x89PNG"));
        eprintln!(
            "PDF system render cancellation completed in {} ms",
            elapsed.as_millis()
        );
    }

    #[cfg(windows)]
    #[test]
    #[ignore = "Run alone with --ignored: process handle counts require no parallel test owners"]
    fn windows_pdf_handle_stress() {
        #[link(name = "kernel32")]
        unsafe extern "system" {
            fn GetCurrentProcess() -> isize;
            fn GetProcessHandleCount(process: isize, count: *mut u32) -> i32;
        }
        fn handles() -> u32 {
            let mut count = 0;
            assert_ne!(
                unsafe { GetProcessHandleCount(GetCurrentProcess(), &mut count) },
                0
            );
            count
        }
        for _ in 0..5 {
            render_page(PDF, 1).unwrap();
            cancel_in_flight();
        }
        let baseline = handles();
        let mut checkpoints = Vec::new();
        for _ in 0..3 {
            for _ in 0..20 {
                render_page(PDF, 1).unwrap();
                cancel_in_flight();
            }
            checkpoints.push(handles());
        }
        eprintln!("PDF handle stress: baseline={baseline}, checkpoints={checkpoints:?}; 60 success + 60 observed in-flight cancellations");
        assert!(
            checkpoints.iter().all(|count| *count <= baseline + 8),
            "unbounded handles: {checkpoints:?}"
        );
    }
}
