use quick_xml::{events::Event, name::ResolveResult};

/// Capability declaration of the local text reader, independent of Office version.
#[derive(Clone,Copy)]
pub(crate) enum OfficeTextFormat { Word, Spreadsheet, Presentation }
impl OfficeTextFormat {
    fn understands(self,ns:&ResolveResult<'_>)->bool {
        let ResolveResult::Bound(n)=ns else{return false};
        match self {
            Self::Word=>matches!(n.as_ref(),b"http://schemas.openxmlformats.org/wordprocessingml/2006/main"|b"http://purl.oclc.org/ooxml/wordprocessingml/main"),
            Self::Spreadsheet=>matches!(n.as_ref(),b"http://schemas.openxmlformats.org/spreadsheetml/2006/main"|b"http://purl.oclc.org/ooxml/spreadsheetml/main"),
            Self::Presentation=>matches!(n.as_ref(),b"http://schemas.openxmlformats.org/presentationml/2006/main"|b"http://purl.oclc.org/ooxml/presentationml/main"|b"http://schemas.openxmlformats.org/drawingml/2006/main"|b"http://purl.oclc.org/ooxml/drawingml/main"),
        }
    }
}
#[derive(Clone, Copy, PartialEq, Eq)]
enum CompatibilityKind { Node, Alternate, Branch }
struct CompatibilityFrame { kind:CompatibilityKind, visible:bool, chosen:bool, choices:bool, fallback:bool }
impl CompatibilityFrame {
    fn new(kind:CompatibilityKind,visible:bool)->Self{Self{kind,visible,chosen:false,choices:false,fallback:false}}
}
/// Select one markup alternative while the original reader retains namespace scope.
/// Each consumer declares its own supported namespaces.
pub(crate) struct OfficeCompatibility { frames:Vec<CompatibilityFrame>,format:OfficeTextFormat }
impl OfficeCompatibility {
    pub(crate) fn new(format:OfficeTextFormat)->Self{Self{frames:Vec::new(),format}}
    pub(crate) fn is_namespace(ns:&ResolveResult<'_>)->bool{matches!(ns,ResolveResult::Bound(n) if n.as_ref()==b"http://schemas.openxmlformats.org/markup-compatibility/2006")}
    fn choice_supported(&self,e:&quick_xml::events::BytesStart<'_>,reader:&quick_xml::NsReader<&[u8]>)->Result<bool,String>{
        let mut requires:Option<String>=None;
        for attr in e.attributes(){let attr=attr.map_err(|_|"Office 兼容分支属性无效")?;
            if attr.key.as_ref()==b"Requires" {
                if requires.is_some(){return Err("Office 兼容分支 Requires 重复".into());}
                requires=Some(attr.decode_and_unescape_value(reader).map_err(|_|"Office 兼容分支属性编码无效")?.into_owned());
            }
        }
        let requires=requires.filter(|s|!s.trim().is_empty()).ok_or("Office 兼容分支缺少 Requires")?;
        let mut supported=true;
        for prefix in requires.split_ascii_whitespace(){
            if prefix.contains(':'){return Err("Office 兼容分支 Requires 前缀无效".into());}
            let name=format!("{prefix}:capability");let(ns,_)=reader.resolve_element(quick_xml::name::QName(name.as_bytes()));
            match ns {quick_xml::name::ResolveResult::Bound(_)=>supported&=self.format.understands(&ns),_=>return Err("Office 兼容分支 Requires 引用未声明命名空间".into())}
        }
        Ok(supported)
    }
    pub(crate) fn admit(&mut self,control:bool,event:&Event<'_>,reader:&quick_xml::NsReader<&[u8]>)->Result<bool,String>{
        if matches!(event,Event::DocType(_)){return Err("Office 不允许外部实体".into());}
        match event {
            Event::Start(e)|Event::Empty(e)=>{
                let inherited=self.frames.last().map(|f|f.visible).unwrap_or(true);
                let frame=if control {match e.local_name().as_ref(){
                    b"AlternateContent"=>{
                        if self.frames.is_empty()||self.frames.last().is_some_and(|f|f.kind==CompatibilityKind::Alternate)||matches!(event,Event::Empty(_)){return Err("Office 兼容内容结构无效".into());}
                        CompatibilityFrame::new(CompatibilityKind::Alternate,inherited)
                    }
                    b"Choice"=>{
                        let supported=self.choice_supported(e,reader)?;
                        let parent=self.frames.last_mut().filter(|f|f.kind==CompatibilityKind::Alternate).ok_or("Office Choice 不在兼容内容中")?;
                        if parent.fallback{return Err("Office Choice 不能位于 Fallback 后".into());}
                        parent.choices=true;let selected=!parent.chosen&&supported;if selected{parent.chosen=true;}
                        CompatibilityFrame::new(CompatibilityKind::Branch,inherited&&selected)
                    }
                    b"Fallback"=>{
                        let parent=self.frames.last_mut().filter(|f|f.kind==CompatibilityKind::Alternate).ok_or("Office Fallback 不在兼容内容中")?;
                        if !parent.choices||parent.fallback{return Err("Office Fallback 缺少候选或重复".into());}
                        parent.fallback=true;CompatibilityFrame::new(CompatibilityKind::Branch,inherited&&!parent.chosen)
                    }
                    _=>return Err("Office 兼容控制元素不受支持".into()),
                }}else{
                    if self.frames.last().is_some_and(|f|f.kind==CompatibilityKind::Alternate){return Err("Office 兼容内容只能包含 Choice 或 Fallback".into());}
                    CompatibilityFrame::new(CompatibilityKind::Node,inherited)
                };
                let admitted=frame.kind==CompatibilityKind::Node&&frame.visible;
                if matches!(event,Event::Start(_)){self.frames.push(frame);if self.frames.len()>256{return Err("Office XML 层级过深".into());}}
                Ok(admitted)
            }
            Event::End(_)=>{
                let frame=self.frames.pop().ok_or("Office XML 无效")?;
                if frame.kind==CompatibilityKind::Alternate&&!frame.choices{return Err("Office 兼容内容缺少 Choice".into());}
                Ok(frame.kind==CompatibilityKind::Node&&frame.visible)
            }
            Event::Eof=>{if !self.frames.is_empty(){return Err("Office XML 不完整".into());}Ok(true)}
            Event::Text(t) if self.frames.last().is_some_and(|f|f.kind==CompatibilityKind::Alternate)=>{
                if !t.unescape().map_err(|_|"Office 文本编码无效")?.chars().all(|c|matches!(c,' '| '\t'|'\n'|'\r')){return Err("Office 兼容内容不能含直接文字".into());}Ok(false)
            }
            Event::CData(t) if self.frames.last().is_some_and(|f|f.kind==CompatibilityKind::Alternate)=>{
                let content:&[u8]=t.as_ref();if !content.iter().all(|c|matches!(*c,b' '|b'\t'|b'\n'|b'\r')){return Err("Office 兼容内容不能含直接文字".into());}Ok(false)
            }
            _=>Ok(self.frames.last().map(|f|f.visible).unwrap_or(true)),
        }
    }
}

