use crate::office_xml_compatibility_v1::{OfficeCompatibility, OfficeTextFormat};
use quick_xml::events::Event;
use std::io::{Cursor, Read};

pub const MAX_ARCHIVE_ENTRIES: usize = 2000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArchiveEntry {
    pub path: String,
    pub is_directory: bool,
    pub byte_count: u64,
}

pub fn archive_entries(bytes: &[u8]) -> Result<Vec<ArchiveEntry>, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "ZIP 文件结构无效")?;
    crate::attachment_document_text_v1::validate_raw_directory(bytes, zip.central_directory_start(), zip.len())?;
    if zip.len() > MAX_ARCHIVE_ENTRIES {
        return Err("ZIP 条目超过预览限制".into());
    }
    let mut entries = Vec::with_capacity(zip.len());
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "ZIP 目录读取失败")?;
        let safe_path = item
            .enclosed_name()
            .ok_or_else(|| "ZIP 包含不安全路径".to_owned())?
            .components()
            .map(|component| component.as_os_str().to_string_lossy().into_owned())
            .collect::<Vec<_>>()
            .join("/");
        entries.push(ArchiveEntry {
            path: safe_path,
            is_directory: item.is_dir(),
            byte_count: item.size(),
        });
    }
    entries.sort_by(|left, right| left.path.cmp(&right.path));
    Ok(entries)
}

pub fn read_archive_entry(
    bytes: &[u8],
    requested_path: &str,
    maximum_bytes: u64,
) -> Result<(ArchiveEntry, Vec<u8>), String> {
    if requested_path.is_empty() || requested_path.len() > 1024 || requested_path.contains('\0') {
        return Err("ZIP 内文件引用无效".into());
    }
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "ZIP 文件结构无效")?;
    crate::attachment_document_text_v1::validate_raw_directory(bytes, zip.central_directory_start(), zip.len())?;
    if zip.len() > MAX_ARCHIVE_ENTRIES {
        return Err("ZIP 条目超过预览限制".into());
    }
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "ZIP 目录读取失败")?;
        let safe_path = item
            .enclosed_name()
            .ok_or_else(|| "ZIP 包含不安全路径".to_owned())?
            .components()
            .map(|component| component.as_os_str().to_string_lossy().into_owned())
            .collect::<Vec<_>>()
            .join("/");
        if safe_path != requested_path {
            continue;
        }
        if item.is_dir() {
            return Err("ZIP 内目录不能直接预览".into());
        }
        if item.size() > maximum_bytes {
            return Err("ZIP 内文件超出安全预览上限".into());
        }
        let entry = ArchiveEntry {
            path: safe_path,
            is_directory: false,
            byte_count: item.size(),
        };
        let mut output = Vec::with_capacity(item.size() as usize);
        item.take(maximum_bytes.saturating_add(1))
            .read_to_end(&mut output)
            .map_err(|_| "ZIP 内文件读取失败")?;
        if output.len() as u64 > maximum_bytes {
            return Err("ZIP 内文件超出安全预览上限".into());
        }
        return Ok((entry, output));
    }
    Err("ZIP 内文件不存在或已变化".into())
}

pub fn archive_directory(bytes: &[u8]) -> Result<String, String> {
    let lines = archive_entries(bytes)?
        .into_iter()
        .map(|item| {
            format!(
                "{}{}",
                item.path,
                if item.is_directory {
                    String::new()
                } else {
                    format!("  ({} bytes)", item.byte_count)
                }
            )
        })
        .collect::<Vec<_>>();
    Ok(lines.join("\n"))
}

fn word_namespace(ns:&quick_xml::name::ResolveResult<'_>)->bool {
    matches!(ns,quick_xml::name::ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/wordprocessingml/2006/main"|b"http://purl.oclc.org/ooxml/wordprocessingml/main"))
}
fn reference_namespace(ns:&quick_xml::name::ResolveResult<'_>)->bool {
    matches!(ns,quick_xml::name::ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/officeDocument/2006/relationships"|b"http://purl.oclc.org/ooxml/officeDocument/relationships"))
}
fn docx_part<R:Read+std::io::Seek>(zip:&mut zip::ZipArchive<R>,path:&str,total:&mut usize)->Result<Vec<u8>,String>{
    let item=zip.by_name(path).map_err(|_|"DOCX 引用的部件不可读")?;
    if item.is_dir(){return Err("DOCX 引用的部件不能是目录".into());}
    let mut xml=Vec::new();item.take(4*1024*1024+1).read_to_end(&mut xml).map_err(|_|"DOCX 正文读取失败")?;
    *total+=xml.len();if xml.len()>4*1024*1024 || *total>12*1024*1024{return Err("DOCX 正文超出预览限制".into());}
    // Validate the entire bounded XML part before namespace/revision filtering.
    // Skipped text is not permission to accept a malformed UTF-8 document.
    std::str::from_utf8(&xml).map_err(|_|"DOCX 文本编码无效")?;Ok(xml)
}

fn section_references(xml:&[u8])->Result<(bool,bool,Vec<(&'static str,String)>),String>{
    let mut reader=quick_xml::NsReader::from_reader(xml);let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Word);let mut stack=Vec::<(bool,bool)>::new();let mut root_word=false;let mut seen_root=false;let mut sections=false;let mut references=Vec::new();let mut slots=std::collections::HashSet::new();
    loop {
        let(ns,event)=reader.read_resolved_event().map_err(|_|"DOCX XML 无效")?;let owned=word_namespace(&ns);let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}
        match event {
            Event::DocType(_)=>return Err("DOCX 不允许外部实体".into()),
            Event::Start(ref e)|Event::Empty(ref e)=>{
                if stack.is_empty(){if seen_root{return Err("DOCX 正文根重复".into());}seen_root=true;root_word=owned&&e.local_name().as_ref()==b"document";}
                let excluded=stack.last().is_some_and(|v|v.1)||owned&&revision_excluded(e.local_name().as_ref());
                if owned&&e.local_name().as_ref()==b"sectPr"{sections=true;}
                let section=owned&&!excluded&&e.local_name().as_ref()==b"sectPr";
                if section {if stack.iter().any(|v|v.0){return Err("DOCX 分节结构无效".into());}sections=true;slots.clear();}
                if owned&&!excluded&&matches!(e.local_name().as_ref(),b"headerReference"|b"footerReference") {
                    if stack.last().map(|v|v.0)!=Some(true){return Err("DOCX 页眉页脚引用不在分节中".into());}
                    let kind=if e.local_name().as_ref()==b"headerReference"{"header"}else{"footer"};let mut id=None;let mut style=None;
                    for a in e.attributes(){let a=a.map_err(|_|"DOCX 分节属性无效")?;let(ns,key)=reader.resolve_attribute(a.key);let value=a.decode_and_unescape_value(&reader).map_err(|_|"DOCX 分节属性编码无效")?.into_owned();
                        if reference_namespace(&ns)&&key.as_ref()==b"id"{id=Some(value);}else if word_namespace(&ns)&&key.as_ref()==b"type"{style=Some(value);}}
                    let id=id.filter(|v|!v.is_empty()&&v.len()<=1024).ok_or("DOCX 页眉页脚缺少关系身份")?;
                    let style=style.filter(|v|matches!(v.as_str(),"default"|"first"|"even")).ok_or("DOCX 页眉页脚类型无效")?;
                    if !slots.insert((kind,style)){return Err("DOCX 同一分节页眉页脚引用重复".into());}references.push((kind,id));
                }
                if matches!(event,Event::Start(_)){stack.push((section,excluded));if stack.len()>256{return Err("DOCX XML 层级过深".into());}}
            }
            Event::End(_)=>{stack.pop().ok_or("DOCX XML 无效")?;}
            Event::Eof=>{if !stack.is_empty()||!seen_root{return Err("DOCX XML 不完整".into());}break;}
            _=>{}
        }
    }
    Ok((root_word,sections,references))
}
fn docx_target(target:&str)->Result<String,String>{
    if target.is_empty()||target.len()>1024||target.contains(['\\',':','?','#','%'])||target.starts_with("//")||target.chars().any(char::is_control){return Err("DOCX 页眉页脚目标不受支持".into());}
    let mut pieces=if target.starts_with('/') {Vec::new()}else{vec!["word"]};
    for piece in target.split('/') {match piece {""|"."=>{},".."=>{pieces.pop().ok_or("DOCX 页眉页脚目标越界")?;},value=>pieces.push(value)}}
    let path=pieces.join("/");if !path.ends_with(".xml"){return Err("DOCX 页眉页脚目标无效".into());}Ok(path)
}
fn referenced_parts(xml:&[u8],references:&[(&'static str,String)],notes:&[DocxNoteReference])->Result<Vec<(String,Option<&'static str>)>,String>{
    let mut reader=quick_xml::NsReader::from_reader(xml);let mut depth=0usize;let mut root=false;let mut rels=std::collections::HashMap::new();
    loop {
        let(ns,event)=reader.read_resolved_event().map_err(|_|"DOCX 关系 XML 无效")?;
        let owned=matches!(ns,quick_xml::name::ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/package/2006/relationships"|b"http://purl.oclc.org/ooxml/package/relationships"));
        match event {
            Event::DocType(_)=>return Err("DOCX 不允许外部实体".into()),
            Event::Start(ref e)|Event::Empty(ref e)=>{
                if depth==0 {if root||!owned||e.local_name().as_ref()!=b"Relationships"{return Err("DOCX 关系根无效".into());}root=true;}
                if depth==1&&owned&&e.local_name().as_ref()==b"Relationship" {
                    let mut attrs=std::collections::HashMap::new();for a in e.attributes(){let a=a.map_err(|_|"DOCX 关系属性无效")?;attrs.insert(a.key.as_ref().to_vec(),a.decode_and_unescape_value(&reader).map_err(|_|"DOCX 关系编码无效")?.into_owned());}
                    let id=attrs.remove(b"Id".as_slice()).filter(|v|!v.is_empty()).ok_or("DOCX 关系缺少身份")?;if rels.insert(id,attrs).is_some(){return Err("DOCX 关系身份重复".into());}
                }
                if matches!(event,Event::Start(_)){depth+=1;if depth>256{return Err("DOCX 关系层级过深".into());}}
            }
            Event::End(_)=>depth=depth.checked_sub(1).ok_or("DOCX 关系 XML 无效")?,
            Event::Eof=>{if depth!=0||!root{return Err("DOCX 关系 XML 不完整".into());}break;}
            _=>{}
        }
    }
    let mut selected=vec![("word/document.xml".into(),None)];let mut seen=std::collections::HashMap::new();
    for (kind,id) in references {
        let rel=rels.get(id).ok_or("DOCX 页眉页脚关系缺失")?;
        let expected=format!("http://schemas.openxmlformats.org/officeDocument/2006/relationships/{kind}");let strict=format!("http://purl.oclc.org/ooxml/officeDocument/relationships/{kind}");
        if !rel.get(b"Type".as_slice()).is_some_and(|v|v==&expected||v==&strict){return Err("DOCX 页眉页脚关系类型无效".into());}
        if rel.get(b"TargetMode".as_slice()).is_some_and(|v|v!="Internal"){return Err("DOCX 不读取外部页眉页脚".into());}
        let path=docx_target(rel.get(b"Target".as_slice()).ok_or("DOCX 页眉页脚目标缺失")?)?;
        if path=="word/document.xml"{return Err("DOCX 正文不能作为页眉页脚部件".into());}
        if let Some(previous)=seen.insert(path.clone(),*kind){if previous!=*kind{return Err("DOCX 同一部件的页眉页脚角色冲突".into());}}else{selected.push((path,Some(if *kind=="header"{"hdr"}else{"ftr"})));}
    }
    for (single,plural) in [("footnote","footnotes"),("endnote","endnotes")] {
        if !notes.iter().any(|(kind,_)|*kind==single){continue;}
        let expected=format!("http://schemas.openxmlformats.org/officeDocument/2006/relationships/{plural}");let strict=format!("http://purl.oclc.org/ooxml/officeDocument/relationships/{plural}");
        let candidates=rels.values().filter(|rel|rel.get(b"Type".as_slice()).is_some_and(|value|value==&expected||value==&strict)).collect::<Vec<_>>();
        if candidates.len()!=1{return Err("DOCX 脚注尾注关系缺失或歧义".into());}
        let rel=candidates.first().ok_or("DOCX 脚注尾注关系缺失")?;
        if rel.get(b"TargetMode".as_slice()).is_some_and(|v|v!="Internal"){return Err("DOCX 不读取外部脚注尾注".into());}
        let path=docx_target(rel.get(b"Target".as_slice()).ok_or("DOCX 脚注尾注目标缺失")?)?;
        if path=="word/document.xml"{return Err("DOCX 正文不能作为脚注尾注部件".into());}
        if let Some(previous)=seen.insert(path.clone(),plural){if previous!=plural{return Err("DOCX 同一部件角色冲突".into());}}else{selected.push((path,Some(plural)));}
    }
    Ok(selected)
}
fn check_part_kind(xml:&[u8],kind:&str)->Result<(),String>{
    let mut reader=quick_xml::NsReader::from_reader(xml);loop {let(ns,event)=reader.read_resolved_event().map_err(|_|"DOCX 页眉页脚 XML 无效")?;match event {
        Event::DocType(_)=>return Err("DOCX 不允许外部实体".into()),
        Event::Start(e)|Event::Empty(e)=>return if word_namespace(&ns)&&e.local_name().as_ref()==kind.as_bytes(){Ok(())}else{Err("DOCX 页眉页脚部件类型不符".into())},
        Event::Eof=>return Err("DOCX 页眉页脚部件为空".into()),_=>{}
    }}
}

type DocxNoteReference=(&'static str,String);
fn note_kind(name:&[u8])->Option<&'static str>{match name {b"footnoteReference"=>Some("footnote"),b"endnoteReference"=>Some("endnote"),_=>None}}
fn note_marker(kind:&str,id:&str)->String{format!("[{} {id}]",if kind=="footnote"{"脚注"}else{"尾注"})}
fn word_attributes(e:&quick_xml::events::BytesStart<'_>,reader:&quick_xml::NsReader<&[u8]>)->Result<std::collections::HashMap<Vec<u8>,String>,String>{
    let mut values=std::collections::HashMap::new();
    for a in e.attributes(){let a=a.map_err(|_|"DOCX 注释属性无效")?;let(ns,key)=reader.resolve_attribute(a.key);if word_namespace(&ns){if values.insert(key.as_ref().to_vec(),a.decode_and_unescape_value(reader).map_err(|_|"DOCX 注释属性编码无效")?.into_owned()).is_some(){return Err("DOCX 注释展开属性重复".into());}}}
    Ok(values)
}
fn note_id(value:Option<&String>)->Result<String,String>{value.and_then(|s|s.trim().parse::<i64>().ok()).map(|n|n.to_string()).ok_or_else(||"DOCX 脚注尾注引用 ID 无效".into())}
fn revision_excluded(name:&[u8])->bool {
    matches!(name,b"del"|b"moveFrom"|b"sectPrChange"|b"pPrChange"|b"rPrChange"|b"tblPrChange"|b"trPrChange"|b"tcPrChange"|b"tblGridChange"|b"tblPrExChange")
}
#[derive(Default)]
struct DocxProjectionFrame {
    excluded:bool,body:bool,word_tag:Option<&'static str>,paragraph_mark_deleted:bool,output_start:usize,references_start:usize,
}
#[derive(Default)]
struct DocxTextProjection {
    output:String,in_text:bool,pending_paragraph_join:bool,stack:Vec<DocxProjectionFrame>,references:Vec<DocxNoteReference>,seen:std::collections::HashSet<DocxNoteReference>,
}
impl DocxTextProjection {
    fn end_paragraph_join(&mut self){if self.pending_paragraph_join{if !self.output.ends_with('\n'){self.output.push('\n');}self.pending_paragraph_join=false;}}
    fn push(&mut self,event:&Event<'_>,owned:bool,reader:&quick_xml::NsReader<&[u8]>,main:bool)->Result<(),String>{
        match event {
            Event::DocType(_)=>return Err("DOCX 不允许外部实体".into()),
            Event::Start(e)|Event::Empty(e)=>{
                let name=e.local_name();let inherited=self.stack.last().map(|f|(f.excluded,f.body)).unwrap_or((false,false));
                // Revision markers in current row/cell properties apply to that
                // container, not merely to the empty marker element itself.
                if owned&&!inherited.0&&self.stack.len()>=2 {
                    let depth=self.stack.len();
                    let target=match name.as_ref(){b"del"=>Some(("trPr","tr")),b"cellDel"=>Some(("tcPr","tc")),_=>None};
                    if let Some((properties,container))=target {
                        if self.stack[depth-1].word_tag==Some(properties)&&self.stack[depth-2].word_tag==Some(container){
                            let frame=&self.stack[depth-2];
                            if self.output.len()!=frame.output_start||self.references.len()!=frame.references_start{return Err("DOCX 表格修订属性晚于内容，无法确认当前文字".into());}
                            self.stack[depth-2].excluded=true;self.stack[depth-1].excluded=true;
                        }
                    }
                }
                // Only current paragraph-mark properties can remove this
                // separator. The paragraph contents remain current text.
                if owned&&!inherited.0&&name.as_ref()==b"del"&&self.stack.len()>=3 {
                    let depth=self.stack.len();
                    if self.stack[depth-1].word_tag==Some("rPr")&&self.stack[depth-2].word_tag==Some("pPr")&&self.stack[depth-3].word_tag==Some("p"){
                        let frame=&self.stack[depth-3];
                        if self.output.len()!=frame.output_start||self.references.len()!=frame.references_start{return Err("DOCX 段落修订属性晚于内容，无法确认当前文字".into());}
                        self.stack[depth-3].paragraph_mark_deleted=true;
                    }
                }
                let excluded=self.stack.last().is_some_and(|f|f.excluded)||owned&&revision_excluded(name.as_ref());let body=inherited.1||owned&&name.as_ref()==b"body";
                if owned&&!excluded {
                    match name.as_ref(){
                        b"p"=>{let pending=self.pending_paragraph_join;self.pending_paragraph_join=false;if pending&&matches!(event,Event::Empty(_)){self.output.push('\n');}},
                        b"tbl"|b"tc"|b"body"|b"hdr"|b"ftr"|b"footnote"|b"endnote"|b"txbxContent"|b"sectPr"=>self.end_paragraph_join(),
                        _=>{}
                    }
                }
                if owned&&!excluded {if let Some(kind)=note_kind(name.as_ref()){
                    if !main||!body{return Err("DOCX 此位置不支持脚注尾注引用".into());}
                    let attrs=word_attributes(e,reader)?;let id=note_id(attrs.get(b"id".as_slice()))?;self.output.push_str(&note_marker(kind,&id));
                    if self.seen.insert((kind,id.clone())){self.references.push((kind,id));}
                }}
                self.in_text=owned&&!excluded&&matches!(event,Event::Start(_))&&name.as_ref()==b"t";
                if owned&&!excluded {match name.as_ref(){b"tab"=>self.output.push('\t'),b"br"|b"cr"=>self.output.push('\n'),b"noBreakHyphen"=>self.output.push('\u{2011}'),b"softHyphen"=>self.output.push('\u{ad}'),_=>{}}}
                if matches!(event,Event::Start(_)){
                    let word_tag=if owned {match name.as_ref(){b"tr"=>Some("tr"),b"tc"=>Some("tc"),b"trPr"=>Some("trPr"),b"tcPr"=>Some("tcPr"),b"p"=>Some("p"),b"pPr"=>Some("pPr"),b"rPr"=>Some("rPr"),b"tbl"=>Some("tbl"),b"body"=>Some("body"),b"hdr"=>Some("hdr"),b"ftr"=>Some("ftr"),b"footnote"=>Some("footnote"),b"endnote"=>Some("endnote"),b"txbxContent"=>Some("txbxContent"),_=>None}}else{None};
                    self.stack.push(DocxProjectionFrame{excluded,body,word_tag,paragraph_mark_deleted:false,output_start:self.output.len(),references_start:self.references.len()});if self.stack.len()>256{return Err("DOCX XML 层级过深".into());}
                }
            }
            Event::Text(e) if self.in_text=>self.output.push_str(&e.unescape().map_err(|_|"DOCX 文本编码无效")?),
            Event::CData(e) if self.in_text=>self.output.push_str(std::str::from_utf8(e.as_ref()).map_err(|_|"DOCX 文本编码无效")?),
            Event::End(_)=>{
                let frame=self.stack.last().ok_or("DOCX XML 无效")?;
                if !frame.excluded {match frame.word_tag{
                    Some("p")=>{if frame.paragraph_mark_deleted{self.pending_paragraph_join=true;}else{self.output.push('\n');self.pending_paragraph_join=false;}},
                    Some("tbl"|"tc"|"body"|"hdr"|"ftr"|"footnote"|"endnote"|"txbxContent")=>self.end_paragraph_join(),
                    _=>{}
                }}
                self.in_text=false;self.stack.pop();
            }
            _=>{}
        }
        Ok(())
    }
    fn finish(mut self)->Result<(String,Vec<DocxNoteReference>),String>{if !self.stack.is_empty(){return Err("DOCX XML 不完整".into());}self.end_paragraph_join();Ok((self.output,self.references))}
}
fn docx_text(xml:&[u8],main:bool)->Result<(String,Vec<DocxNoteReference>),String>{
    let mut reader=quick_xml::NsReader::from_reader(xml);let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Word);let mut projection=DocxTextProjection::default();
    loop {let(ns,event)=reader.read_resolved_event().map_err(|_|"DOCX XML 无效")?;let owned=word_namespace(&ns);let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}if matches!(event,Event::Eof){break;}projection.push(&event,owned,&reader,main)?;}
    projection.finish()
}
fn note_records(xml:&[u8],kind:&'static str,needed:&std::collections::HashSet<String>)->Result<std::collections::HashMap<String,String>,String>{
    std::str::from_utf8(xml).map_err(|_|"DOCX 脚注尾注编码无效")?;
    let xml=xml.strip_prefix(b"\xef\xbb\xbf").unwrap_or(xml);
    let plural=if kind=="footnote"{"footnotes"}else{"endnotes"};let mut reader=quick_xml::NsReader::from_reader(xml);let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Word);let mut depth=0usize;let mut root=false;
    let mut current:Option<(String,bool,Option<DocxTextProjection>)>=None;let mut records=std::collections::HashMap::<String,(bool,Option<String>)>::new();
    loop {
        let(ns,event)=reader.read_resolved_event().map_err(|_|"DOCX 脚注尾注 XML 无效")?;let owned=word_namespace(&ns);let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}
        if let Some((_,_,Some(projection)))=current.as_mut(){projection.push(&event,owned,&reader,false)?;}
        match event {
            Event::DocType(_)=>return Err("DOCX 不允许外部实体".into()),
            Event::Start(ref e)|Event::Empty(ref e)=>{
                let name=e.local_name();
                if depth==0 {if root||!owned||name.as_ref()!=plural.as_bytes(){return Err("DOCX 脚注尾注部件类型不符".into());}root=true;}
                if owned&&note_kind(name.as_ref()).is_some(){return Err("DOCX 脚注尾注不支持嵌套引用".into());}
                if owned&&name.as_ref()==kind.as_bytes(){
                    if depth!=1{return Err("DOCX 脚注尾注记录层级无效".into());}
                    let attrs=word_attributes(e,&reader)?;let id=note_id(attrs.get(b"id".as_slice()))?;
                    let style=attrs.get(b"type".as_slice()).map(String::as_str).unwrap_or("normal");
                    if !matches!(style,"normal"|"separator"|"continuationSeparator"|"continuationNotice"){return Err("DOCX 脚注尾注记录类型无效".into());}
                    if records.contains_key(&id){return Err("DOCX 脚注尾注 ID 重复".into());}
                    let normal=style=="normal";let mut projection=(normal&&needed.contains(&id)).then(DocxTextProjection::default);
                    if let Some(projection)=projection.as_mut(){projection.push(&event,owned,&reader,false)?;}
                    if matches!(event,Event::Empty(_)){let text=projection.map(DocxTextProjection::finish).transpose()?.map(|v|v.0);records.insert(id,(normal,text));}
                    else {current=Some((id,normal,projection));}
                }
                if matches!(event,Event::Start(_)){depth+=1;if depth>256{return Err("DOCX 脚注尾注层级过深".into());}}
            }
            Event::End(_)=>{
                if depth==2 {if let Some((id,normal,projection))=current.take(){let text=projection.map(DocxTextProjection::finish).transpose()?.map(|v|v.0);if records.insert(id,(normal,text)).is_some(){return Err("DOCX 脚注尾注 ID 重复".into());}}}
                depth=depth.checked_sub(1).ok_or("DOCX 脚注尾注 XML 无效")?;
            }
            Event::Eof=>{if depth!=0||!root||current.is_some(){return Err("DOCX 脚注尾注 XML 不完整".into());}break;}
            _=>{}
        }
    }
    let mut result=std::collections::HashMap::new();
    for id in needed {let(normal,text)=records.get(id).ok_or("DOCX 引用的脚注尾注记录缺失")?;if !normal{return Err("DOCX 正文不能引用分隔类型脚注尾注".into());}
        result.insert(id.clone(),text.as_ref().ok_or("DOCX 引用的脚注尾注内容缺失")?.clone());
    }
    Ok(result)
}

pub fn extract(bytes: &[u8]) -> Result<String, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "DOCX 文件结构无效")?;
    crate::attachment_document_text_v1::validate_raw_directory(bytes, zip.central_directory_start(), zip.len())?;
    if zip.len() > 2000 {
        return Err("DOCX 包含过多条目".into());
    }
    let mut parts = Vec::new();
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "DOCX 条目不可读")?;
        if item.enclosed_name().is_none() {
            return Err("DOCX 包含不安全路径".into());
        }
        if item.is_dir() && item.size() != 0 {
            return Err("DOCX 目录条目含异常内容".into());
        }
        let name = item.name();
        if name == "word/document.xml"
            || (name.starts_with("word/header") || name.starts_with("word/footer"))
                && name.ends_with(".xml")
        {
            parts.push(name.to_owned());
        }
    }
    parts.sort_by_key(|name| (name != "word/document.xml", name.clone()));
    let mut total=0;
    let document=Some(docx_part(&mut zip,"word/document.xml",&mut total)?);
    let (root_word,sections,references)=section_references(document.as_deref().ok_or("DOCX 正文缺失")?)?;
    let (main_text,note_refs)=docx_text(document.as_deref().ok_or("DOCX 正文缺失")?,true)?;
    let has_relationships=zip.file_names().any(|name|name=="word/_rels/document.xml.rels");
    let parts=if has_relationships {
        if !root_word{return Err("DOCX 分节正文根无效".into());}
        let xml=docx_part(&mut zip,"word/_rels/document.xml.rels",&mut total)?;
        referenced_parts(&xml,&references,&note_refs)?
    }else if !references.is_empty()||!note_refs.is_empty(){return Err("DOCX 引用关系文件缺失".into());}
    else if sections {vec![("word/document.xml".into(),None)]}
    else {parts.into_iter().map(|name|(name,None)).collect()};
    let mut output=String::new();let mut note_text=std::collections::HashMap::new();
    for (name,kind) in parts {
        if name=="word/document.xml"&&kind.is_none(){output.push_str(&main_text);continue;}
        let xml=docx_part(&mut zip,&name,&mut total)?;
        if matches!(kind,Some("footnotes"|"endnotes")){
            let single=if kind==Some("footnotes"){"footnote"}else{"endnote"};
            let needed=note_refs.iter().filter(|(k,_)|*k==single).map(|(_,id)|id.clone()).collect();
            for(id,text)in note_records(&xml,single,&needed)?{note_text.insert((single,id),text);}
        }else{
            if let Some(kind)=kind{check_part_kind(&xml,kind)?;}
            let(text,_)=docx_text(&xml,false)?;output.push_str(&text);
        }
    }
    for reference in &note_refs {
        let text=note_text.get(reference).ok_or("DOCX 引用的脚注尾注内容缺失")?;
        if !output.ends_with('\n'){output.push('\n');}output.push_str(&note_marker(reference.0,&reference.1));output.push('\n');output.push_str(text);
        if !output.ends_with('\n'){output.push('\n');}
    }
    if output.trim().is_empty() {
        return Err("DOCX 没有可读取的正文".into());
    }
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    fn archive(xml: &str) -> Vec<u8> {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file(
                "word/document.xml",
                zip::write::SimpleFileOptions::default(),
            )
            .unwrap();
        writer.write_all(xml.as_bytes()).unwrap();
        writer.finish().unwrap().into_inner()
    }

    fn compatibility_document(inner:&str)->String {format!("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:new='urn:synthetic-unsupported'><w:body>{inner}</w:body></w:document>")}
    fn compatibility_text(text:&str)->String{format!("<w:p><w:r><w:t>{text}</w:t></w:r></w:p>")}

    #[test] fn compatibility_requires_all_declared_prefixes_and_handles_empty_selected_choice(){
        for (branch,expected) in [
            ("<mc:Choice Requires='w new'><w:p><w:r><w:t>UNSELECTED</w:t></w:r></w:p></mc:Choice><mc:Fallback><w:p><w:r><w:t>FALLBACK</w:t></w:r></w:p></mc:Fallback>","BEFORE\nFALLBACK\n"),
            ("<mc:Choice xmlns:q='http://purl.oclc.org/ooxml/wordprocessingml/main' Requires='q'><q:p><q:r><q:t>ALIASED</q:t></q:r></q:p></mc:Choice><mc:Fallback><w:p><w:r><w:t>UNSELECTED</w:t></w:r></w:p></mc:Fallback>","BEFORE\nALIASED\n"),
            ("<mc:Choice Requires='w'/><mc:Fallback><w:p><w:r><w:t>UNSELECTED</w:t></w:r></w:p></mc:Fallback>","BEFORE\n"),
        ] {let xml=compatibility_document(&format!("{}<mc:AlternateContent>{branch}</mc:AlternateContent>",compatibility_text("BEFORE")));assert_eq!(extract(&archive(&xml)).unwrap(),expected);}
    }
    #[test] fn compatibility_notes_keep_selected_record_scope_without_nested_unselected_reference(){
        let xml=compatibility_document("<w:p><w:r><w:t>BODY</w:t><mc:AlternateContent><mc:Choice Requires='new'><w:footnoteReference w:id='99'/></mc:Choice><mc:Fallback><w:footnoteReference w:id='2'/></mc:Fallback></mc:AlternateContent></w:r></w:p>");
        let mut zip=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for(path,xml)in [("word/document.xml",xml.as_str()),("word/_rels/document.xml.rels","<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='notes' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes' Target='footnotes.xml'/></Relationships>"),("word/footnotes.xml","<w:footnotes xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:new='urn:synthetic-unsupported'><mc:AlternateContent><mc:Choice Requires='new'><w:footnote w:id='99'><w:p><w:r><w:t>UNSELECTED_NOTE</w:t></w:r></w:p></w:footnote></mc:Choice><mc:Fallback xmlns:q='http://purl.oclc.org/ooxml/wordprocessingml/main'><q:footnote q:id='2'><q:p><q:r><mc:AlternateContent><mc:Choice Requires='q'><q:t><![CDATA[NOTE & 原文]]></q:t></mc:Choice><mc:Fallback><q:footnoteReference q:id='99'/></mc:Fallback></mc:AlternateContent></q:r></q:p></q:footnote></mc:Fallback></mc:AlternateContent></w:footnotes>")]{zip.start_file(path,zip::write::SimpleFileOptions::default()).unwrap();zip.write_all(xml.as_bytes()).unwrap();}
        let bytes=zip.finish().unwrap().into_inner();assert_eq!(extract(&bytes).unwrap(),"BODY[脚注 2]\n[脚注 2]\nNOTE & 原文\n");assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::DOCX).unwrap(),"BODY[脚注 2]\n[脚注 2]\nNOTE & 原文\n");
    }
    #[test] fn compatibility_unselected_branches_keep_depth_and_dtd_safety_gates(){
        let deep=format!("<mc:AlternateContent><mc:Choice Requires='new'>{}HIDDEN{}</mc:Choice><mc:Fallback>{}</mc:Fallback></mc:AlternateContent>","<w:r>".repeat(257),"</w:r>".repeat(257),compatibility_text("SAFE"));assert!(extract(&archive(&compatibility_document(&deep))).is_err());
        let xml=compatibility_document("<mc:AlternateContent><mc:Choice Requires='new'><!DOCTYPE t><w:p/></mc:Choice><mc:Fallback><w:p><w:r><w:t>SAFE</w:t></w:r></w:p></mc:Fallback></mc:AlternateContent>");assert!(extract(&archive(&xml)).is_err());
    }
    #[test] fn compatibility_uses_first_understood_choice_instead_of_all_branches(){
        let inner=format!("<mc:AlternateContent><mc:Choice Requires='new'>{}</mc:Choice><mc:Choice Requires='w'>{}</mc:Choice><mc:Choice Requires='w'>{}</mc:Choice><mc:Fallback>{}</mc:Fallback></mc:AlternateContent>",compatibility_text("UNSELECTED_NEW"),compatibility_text("SELECTED_FIRST"),compatibility_text("UNSELECTED_SECOND"),compatibility_text("UNSELECTED_FALLBACK"));
        let bytes=archive(&compatibility_document(&inner));assert_eq!(extract(&bytes).unwrap(),"SELECTED_FIRST\n");assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::DOCX).unwrap(),"SELECTED_FIRST\n");
    }
    #[test] fn compatibility_fallback_preserves_namespace_scope_and_nested_selection(){
        let inner="<mc:AlternateContent><mc:Choice Requires='new'><w:p><w:r><w:t>UNSELECTED_NEW</w:t></w:r></w:p></mc:Choice><mc:Fallback xmlns:q='http://purl.oclc.org/ooxml/wordprocessingml/main'><q:p><q:r><q:t>FALLBACK_</q:t><mc:AlternateContent><mc:Choice Requires='q'><q:t>NESTED</q:t></mc:Choice><mc:Fallback><q:t>UNSELECTED_NESTED</q:t></mc:Fallback></mc:AlternateContent></q:r></q:p></mc:Fallback></mc:AlternateContent>";
        assert_eq!(extract(&archive(&compatibility_document(inner))).unwrap(),"FALLBACK_NESTED\n");
    }
    #[test] fn compatibility_no_supported_choice_without_fallback_does_not_leak(){
        let inner=format!("{}<mc:AlternateContent><mc:Choice Requires='new'>{}</mc:Choice></mc:AlternateContent>{}",compatibility_text("BEFORE"),compatibility_text("UNSELECTED_NEW"),compatibility_text("AFTER"));assert_eq!(extract(&archive(&compatibility_document(&inner))).unwrap(),"BEFORE\nAFTER\n");
    }
    #[test] fn compatibility_foreign_local_names_are_not_control_elements(){
        let xml="<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' xmlns:mc='urn:synthetic-foreign'><w:body><mc:AlternateContent><mc:Choice><w:p><w:r><w:t>LITERAL</w:t></w:r></w:p></mc:Choice></mc:AlternateContent></w:body></w:document>";assert_eq!(extract(&archive(xml)).unwrap(),"LITERAL\n");
    }
    #[test] fn compatibility_rejects_invalid_branch_structure_and_prefixes(){
        for inner in ["<mc:AlternateContent/>","<mc:AlternateContent><mc:Fallback/></mc:AlternateContent>","<mc:AlternateContent><mc:Choice/></mc:AlternateContent>","<mc:AlternateContent><mc:Choice Requires=''/></mc:AlternateContent>","<mc:AlternateContent><mc:Choice Requires='missing'/></mc:AlternateContent>","<mc:AlternateContent><mc:Choice Requires='w'/><mc:Fallback/><mc:Fallback/></mc:AlternateContent>","<mc:AlternateContent><mc:Choice Requires='new'/><mc:Fallback/><mc:Choice Requires='w'/></mc:AlternateContent>","<mc:Choice Requires='w'/>","<mc:AlternateContent>UNEXPECTED<w:p/></mc:AlternateContent>"]{
            let xml=compatibility_document(&format!("{}{inner}",compatibility_text("SAFE")));assert!(extract(&archive(&xml)).is_err(),"{inner}");
        }
    }
    #[test] fn compatibility_sections_skip_unselected_missing_header_and_choose_actual_part(){
        let xml=compatibility_document("<mc:AlternateContent><mc:Choice Requires='new'><w:sectPr><w:headerReference xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' r:id='absent' w:type='default'/></w:sectPr></mc:Choice><mc:Fallback><w:p><w:r><w:t>BODY</w:t></w:r></w:p><w:sectPr><w:headerReference xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' r:id='chosen' w:type='default'/></w:sectPr></mc:Fallback></mc:AlternateContent>");
        let mut zip=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for(path,xml)in [("word/document.xml",xml.as_str()),("word/_rels/document.xml.rels","<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='chosen' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/header' Target='header1.xml'/></Relationships>"),("word/header1.xml","<w:hdr xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'><mc:AlternateContent><mc:Choice Requires='w'><w:p><w:r><w:t>HEADER</w:t></w:r></w:p></mc:Choice><mc:Fallback><w:p><w:r><w:t>UNSELECTED_HEADER</w:t></w:r></w:p></mc:Fallback></mc:AlternateContent></w:hdr>")]{zip.start_file(path,zip::write::SimpleFileOptions::default()).unwrap();zip.write_all(xml.as_bytes()).unwrap();}
        assert_eq!(extract(&zip.finish().unwrap().into_inner()).unwrap(),"BODY\nHEADER\n");
    }
    #[test]
    fn run_hyphens_keep_empty_and_expanded_characters_in_preview_and_material() {
        for (nonbreak,soft) in [("<w:noBreakHyphen/>","<w:softHyphen/>"),("<w:noBreakHyphen></w:noBreakHyphen>","<w:softHyphen></w:softHyphen>")] {
            let xml=format!("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:p><w:r><w:t>AB</w:t>{nonbreak}<w:t>12</w:t><w:tab/><w:t>co</w:t>{soft}<w:t>operate</w:t><w:br/><w:t><![CDATA[A-B ‑ \u{ad} & 原文]]></w:t></w:r></w:p></w:body></w:document>");
            let bytes=archive(&xml);let expected="AB\u{2011}12\tco\u{ad}operate\nA-B ‑ \u{ad} & 原文\n";
            assert_eq!(extract(&bytes).unwrap(),expected);
            assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::DOCX).unwrap(),expected);
        }
    }
    #[test]
    fn run_hyphens_keep_header_footer_text_without_removing_ruby_content() {
        let mut writer=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (path,xml) in [
            ("word/document.xml","<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:p><w:r><w:t>正文</w:t></w:r><w:ruby><w:rt><w:r><w:t>とうきょう</w:t></w:r></w:rt><w:rubyBase><w:r><w:t>東京</w:t></w:r></w:rubyBase></w:ruby></w:p></w:document>"),
            ("word/header1.xml","<w:hdr xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:p><w:r><w:t>H</w:t><w:noBreakHyphen/><w:t>1</w:t></w:r></w:p></w:hdr>"),
            ("word/footer1.xml","<w:ftr xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:p><w:r><w:t>foot</w:t><w:softHyphen></w:softHyphen><w:t>er</w:t></w:r></w:p></w:ftr>")
        ] {
            writer.start_file(path,zip::write::SimpleFileOptions::default()).unwrap();writer.write_all(xml.as_bytes()).unwrap();
        }
        let bytes=writer.finish().unwrap().into_inner();let expected="正文とうきょう東京\nfoot\u{ad}er\nH\u{2011}1\n";
        assert_eq!(extract(&bytes).unwrap(),expected);
        assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::DOCX).unwrap(),expected);
    }

    #[test]
    fn expanded_run_separators_match_empty_tags_in_preview_and_material() {
        let bytes = archive("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:p><w:r><w:t>收入100</w:t><w:br w:type='textWrapping' w:clear='all'></w:br><w:t>成本60</w:t><w:tab></w:tab><w:t>净利40</w:t><w:cr></w:cr><w:br/><w:t>尾段</w:t></w:r></w:p></w:body></w:document>");
        let expected = "收入100\n成本60\t净利40\n\n尾段\n";
        assert_eq!(extract(&bytes).unwrap(), expected);
        assert_eq!(crate::attachment_document_text_v1::extract(&bytes, crate::attachment_document_text_v1::DOCX).unwrap(), expected);
    }
    #[test]
    fn docx_cdata_is_literal_and_preserves_mixed_text_order() {
        let bytes = archive("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:p><w:r><w:t>前 &amp; <![CDATA[<原文>&amp;😀]]> 后</w:t><w:tab/><w:t>尾</w:t><w:br/><w:t><![CDATA[下一行]]></w:t></w:r></w:p></w:document>");
        assert_eq!(extract(&bytes).unwrap(), "前 & <原文>&amp;😀 后\t尾\n下一行\n");
    }
    #[test]
    fn docx_rejects_directory_entries_with_payload() {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        writer.start_file("word/document.xml", zip::write::SimpleFileOptions::default()).unwrap();
        writer.write_all(b"<w:p xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:t>body</w:t></w:p>").unwrap();
        writer.start_file("word/dirxxX", zip::write::SimpleFileOptions::default()).unwrap();
        writer.write_all(b"unexpected directory payload").unwrap();
        let mut bytes = writer.finish().unwrap().into_inner();
        // Both local and central names become a directory while retaining actual payload bytes.
        let positions: Vec<_> = bytes.windows(11).enumerate().filter_map(|(i, name)| (name == b"word/dirxxX").then_some(i)).collect();
        assert_eq!(positions.len(), 2);
        for position in positions { bytes[position + 10] = b'/'; }
        assert!(extract(&bytes).unwrap_err().contains("目录条目"));
    }
    #[test]
    fn docx_rejects_invalid_cdata_encoding_instead_of_omitting_it() {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        writer.start_file("word/document.xml", zip::write::SimpleFileOptions::default()).unwrap();
        writer.write_all(b"<w:p xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:t>valid<![CDATA[\xff]]></w:t></w:p>").unwrap();
        let bytes = writer.finish().unwrap().into_inner();
        assert!(extract(&bytes).unwrap_err().contains("文本编码"));
    }
    #[test]
    fn docx_reads_inert_text_and_zip_lists_without_extraction() {
        let bytes = archive("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:p><w:r><w:t>正文 &amp; 内容</w:t></w:r></w:p></w:document>");
        assert_eq!(extract(&bytes).unwrap(), "正文 & 内容\n");
        assert!(archive_directory(&bytes)
            .unwrap()
            .contains("word/document.xml"));
        assert!(extract(&archive("<!DOCTYPE x SYSTEM 'file:///etc/passwd'><x/>")).is_err());
        assert!(extract(b"not a zip").is_err());
    }

    #[test]
    fn archive_entries_keep_safe_paths_and_bound_entry_reads() {
        let bytes = archive("local");
        let entries = archive_entries(&bytes).unwrap();
        assert_eq!(entries[0].path, "word/document.xml");
        assert_eq!(
            read_archive_entry(&bytes, "word/document.xml", 5)
                .unwrap()
                .1,
            b"local"
        );
        assert!(read_archive_entry(&bytes, "word/document.xml", 4)
            .unwrap_err()
            .contains("上限"));
        assert!(read_archive_entry(&bytes, "../word/document.xml", 8).is_err());
    }
}

#[cfg(test)]
mod section_parts_tests {
    use super::*;
    use std::io::Write;
    const W:&str="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    const R:&str="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    fn zip(parts:&[(&str,String)])->Vec<u8>{let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));for(p,b)in parts{z.start_file(*p,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(b.as_bytes()).unwrap();}z.finish().unwrap().into_inner()}
    fn document(refs:&str)->String{format!("<q:document xmlns:q='{W}' xmlns:z='{R}'><q:body><q:p><q:r><q:t>BODY</q:t></q:r></q:p><q:sectPr>{refs}</q:sectPr></q:body></q:document>")}
    fn rels(body:&str)->String{format!("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>{body}</Relationships>")}
    fn header()->String{format!("<q:hdr xmlns:q='{W}'><q:p><q:r><q:t>HEADER</q:t></q:r></q:p></q:hdr>")}
    #[test]
    fn section_parts_follow_real_references_and_preserve_entities_once(){
        let bytes=include_bytes!("../../../docs/review/20261019-windows-docx-section-parts/section-parts.docx");let expected="正文原文 A &amp; B\n真实引用页眉\n真实引用页脚\n";
        assert_eq!(extract(bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(bytes,crate::attachment_document_text_v1::DOCX).unwrap(),expected);
    }
    #[test]
    fn section_parts_none_means_no_orphan_and_shared_header_only_once(){
        let bytes=zip(&[("word/document.xml",document("")),("word/header1.xml",header())]);assert_eq!(extract(&bytes).unwrap(),"BODY\n");
        let doc=document("<q:headerReference q:type='default' z:id='h'/>").replace("</q:body>","<q:p><q:pPr><q:sectPr><q:headerReference q:type='default' z:id='h'/></q:sectPr></q:pPr></q:p></q:body>");
        let bytes=zip(&[("word/document.xml",doc),("word/_rels/document.xml.rels",rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/>"))),("word/header1.xml",header())]);assert_eq!(extract(&bytes).unwrap(),"BODY\n\nHEADER\n");
    }
    #[test]
    fn section_parts_strict_aliases_absolute_target_and_unrelated_external_link(){
        let doc=document("<q:headerReference q:type='default' z:id='h'/>").replace(W,"http://purl.oclc.org/ooxml/wordprocessingml/main").replace(R,"http://purl.oclc.org/ooxml/officeDocument/relationships");
        let rel=rels("<Relationship Id='h' Type='http://purl.oclc.org/ooxml/officeDocument/relationships/header' Target='/word/odd.xml'/><Relationship Id='link' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink' Target='https://example.org/' TargetMode='External'/>");
        let bytes=zip(&[("word/document.xml",doc),("word/_rels/document.xml.rels",rel),("word/odd.xml",header().replace(W,"http://purl.oclc.org/ooxml/wordprocessingml/main"))]);assert_eq!(extract(&bytes).unwrap(),"BODY\nHEADER\n");
    }
    #[test]
    fn section_parts_bad_references_cannot_fall_back_to_filename_scan(){
        for relation in [String::new(),format!("<Relationship Id='h' Type='{R}/header' Target='missing.xml'/>"),format!("<Relationship Id='h' Type='{R}/footer' Target='header1.xml'/>"),format!("<Relationship Id='h' Type='{R}/header' Target='https://example.org/x.xml' TargetMode='External'/>"),format!("<Relationship Id='h' Type='{R}/header' Target='../../outside.xml'/>"),format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/><Relationship Id='h' Type='{R}/header' Target='header1.xml'/>")]{
            let bytes=zip(&[("word/document.xml",document("<q:headerReference q:type='default' z:id='h'/>")),("word/_rels/document.xml.rels",rels(&relation)),("word/header1.xml",header())]);assert!(extract(&bytes).is_err(),"Bad relationship accepted: {relation}");
        }
        let bytes=zip(&[("word/document.xml",document("<q:headerReference q:type='default' z:id='h'/>")),("word/header1.xml",header())]);assert!(extract(&bytes).is_err());
    }
    #[test]
    fn section_parts_reject_ambiguous_slots_wrong_root_and_dtd(){
        for (doc,rel,part) in [
            (document("<q:headerReference q:type='default' z:id='h'/><q:headerReference q:type='default' z:id='h'/>"),rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/>")),header()),
            (document("<q:headerReference q:type='default' z:id='h'/>"),rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/>")),header().replace("q:hdr","q:ftr")),
            (document("<q:headerReference q:type='default' z:id='h'/>"),format!("<!DOCTYPE x [<!ENTITY sneaky 'no'>]>{}",rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/>"))),header()),
            (document("<q:headerReference q:type='default' z:id='h'/>").replace("z:id=","id="),rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/>")),header()),
        ]{let bytes=zip(&[("word/document.xml",doc),("word/_rels/document.xml.rels",rel),("word/header1.xml",part)]);assert!(extract(&bytes).is_err());}
    }
    #[test]
    fn section_parts_reject_same_part_with_conflicting_roles(){
        let doc=document("<q:headerReference q:type='default' z:id='h'/><q:footerReference q:type='default' z:id='f'/>");
        let rel=rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/><Relationship Id='f' Type='{R}/footer' Target='header1.xml'/>"));
        let bytes=zip(&[("word/document.xml",doc),("word/_rels/document.xml.rels",rel),("word/header1.xml",header())]);assert!(extract(&bytes).is_err(),"One XML part cannot silently become both header and footer");
    }
    #[test]
    fn section_parts_relationship_xml_shares_existing_resource_limits(){
        let large=rels(&" ".repeat(4*1024*1024));let bytes=zip(&[("word/document.xml",document("")),("word/_rels/document.xml.rels",large)]);assert!(extract(&bytes).unwrap_err().contains("预览限制"));
        fn padded(root:&str,body:&str)->String{let a=format!("<{root} xmlns:q='{W}' xmlns:z='{R}'><q:p><q:r><q:t>");let b=format!("</q:t></q:r></q:p>{body}</{root}>");format!("{a}{}{b}","x".repeat(4*1024*1024-a.len()-b.len()))}
        let section="<q:sectPr><q:headerReference q:type='default' z:id='h'/><q:headerReference q:type='first' z:id='h2'/></q:sectPr>";
        let bytes=zip(&[("word/document.xml",padded("q:document",section)),("word/_rels/document.xml.rels",rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header1.xml'/><Relationship Id='h2' Type='{R}/header' Target='header2.xml'/>"))),("word/header1.xml",padded("q:hdr","")),("word/header2.xml",padded("q:hdr",""))]);
        assert!(extract(&bytes).unwrap_err().contains("预览限制"),"Relationships must count toward the existing 12 MiB combined XML limit");
    }
    #[test]
    fn section_parts_main_document_alias_is_an_error_not_a_panic(){
        let doc=document("<q:headerReference q:type='default' z:id='h'/>");let rel=rels(&format!("<Relationship Id='h' Type='{R}/header' Target='document.xml'/>"));
        let bytes=zip(&[("word/document.xml",doc),("word/_rels/document.xml.rels",rel)]);assert!(extract(&bytes).is_err());
    }
    #[test]
    fn section_parts_missing_body_is_not_a_header_only_document(){assert!(extract(&zip(&[("word/header1.xml",header())])).is_err());}
}

#[cfg(test)]
mod note_reference_tests {
    use super::*;
    use std::io::Write;
    const W:&str="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    const R:&str="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    fn zip(parts:&[(&str,String)])->Vec<u8>{let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));for(p,b)in parts{z.start_file(*p,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(b.as_bytes()).unwrap();}z.finish().unwrap().into_inner()}
    fn doc(refs:&str)->String{format!("<w:document xmlns:w='{W}'><w:body><w:p><w:r><w:t>BODY</w:t>{refs}</w:r></w:p><w:sectPr/></w:body></w:document>")}
    fn rels(rows:&str)->String{format!("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>{rows}</Relationships>")}
    fn relation(target:&str)->String{format!("<Relationship Id='n' Type='{R}/footnotes' Target='{target}'/>")}
    fn notes(rows:&str)->String{format!("<w:footnotes xmlns:w='{W}'>{rows}</w:footnotes>")}
    fn note(id:&str,body:&str)->String{format!("<w:footnote w:id='{id}'><w:p><w:r><w:t>{body}</w:t></w:r></w:p></w:footnote>")}
    fn package(refs:&str,rows:&str,part:String)->Vec<u8>{zip(&[("word/document.xml",doc(refs)),("word/_rels/document.xml.rels",rels(rows)),("word/notes.xml",part)])}
    #[test]
    fn note_reference_real_ids_and_renamed_parts_keep_only_referenced_normal_records(){
        let bytes=include_bytes!("../../../docs/review/20261019-windows-docx-note-references/note-references.docx");
        let expected="正文甲[脚注 2]正文乙[尾注 3]再引[脚注 2]\n[脚注 2]\n脚注原文 A &amp; B‑连接\n原文<&amp;>\n[尾注 3]\n尾注原文\n";
        assert_eq!(extract(bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(bytes,crate::attachment_document_text_v1::DOCX).unwrap(),expected);
    }
    #[test]
    fn note_reference_expanded_elements_and_repeated_numeric_identity_render_once(){
        let bytes=package("<w:footnoteReference w:id='+002'></w:footnoteReference><w:footnoteReference w:id='2'/>",&relation("notes.xml"),notes(&note("02","NOTE")));
        assert_eq!(extract(&bytes).unwrap(),"BODY[脚注 2][脚注 2]\n[脚注 2]\nNOTE\n");
    }
    #[test]
    fn note_reference_strict_alias_zero_normal_and_bom_are_supported(){
        let document=doc("<w:footnoteReference w:id='0'/>").replace("w:","q:").replace("xmlns:w=","xmlns:q=").replace(W,"http://purl.oclc.org/ooxml/wordprocessingml/main");
        let part=format!("\u{feff}<?xml version='1.0' encoding='UTF-8'?><footnotes xmlns='http://purl.oclc.org/ooxml/wordprocessingml/main' xmlns:x='http://purl.oclc.org/ooxml/wordprocessingml/main'><footnote x:id='0' x:type='normal'><p><r><t>ZERO</t></r></p></footnote></footnotes>");
        let rows=relation("/word/notes.xml").replace(R,"http://purl.oclc.org/ooxml/officeDocument/relationships");let bytes=zip(&[("word/document.xml",document),("word/_rels/document.xml.rels",rels(&rows)),("word/notes.xml",part)]);assert_eq!(extract(&bytes).unwrap(),"BODY[脚注 0]\n[脚注 0]\nZERO\n");
    }
    #[test]
    fn note_reference_missing_ambiguous_external_and_main_alias_return_errors(){
        for rows in [String::new(),relation("missing.xml"),relation("document.xml"),format!("{}{}",relation("notes.xml"),relation("notes.xml").replace("Id='n'","Id='n2'")),relation("https://example.org/notes.xml").replace("/>"," TargetMode='External'/>"),relation("../../notes.xml")]{assert!(extract(&package("<w:footnoteReference w:id='2'/>",&rows,notes(&note("2","NOTE")))).is_err(),"{rows}");}
        assert!(extract(&zip(&[("word/document.xml",doc("<w:footnoteReference w:id='2'/>")),("word/notes.xml",notes(&note("2","NOTE")))])).is_err());
    }
    #[test]
    fn note_reference_missing_duplicate_special_and_nested_records_return_errors(){
        for part in [notes(&note("3","WRONG")),notes(&(note("2","A")+&note("02","B"))),notes(&note("2","A").replace("w:id='2'","w:id='2' w:type='separator'")),notes(&note("2","A").replace("</w:r>","<w:footnoteReference w:id='2'/></w:r>")),notes(&note("2","A")).replace("w:footnotes","w:endnotes")]{assert!(extract(&package("<w:footnoteReference w:id='2'/>",&relation("notes.xml"),part)).is_err());}
        assert!(extract(&package("<w:footnoteReference w:id='not-a-number'/>",&relation("notes.xml"),notes(&note("2","NOTE")))).is_err());
    }
    #[test]
    fn note_reference_deleted_refs_and_unreferenced_parts_do_not_add_note_text(){
        let refs="<w:del><w:r><w:delText>DELETED</w:delText><w:footnoteReference w:id='9'/></w:r></w:del><w:ins><w:footnoteReference w:id='2'/></w:ins>";
        let bytes=package(refs,&relation("notes.xml"),notes(&(note("9","DELETED_NOTE")+&note("2","NOTE"))));assert_eq!(extract(&bytes).unwrap(),"BODY[脚注 2]\n[脚注 2]\nNOTE\n");
        assert_eq!(extract(&package("",&relation("missing.xml"),notes(&note("2","UNREFERENCED")))).unwrap(),"BODY\n");
    }
    #[test]
    fn note_reference_notes_xml_obeys_the_existing_part_size_limit(){
        let part=notes(&(note("2","NOTE")+&" ".repeat(4*1024*1024)));assert!(extract(&package("<w:footnoteReference w:id='2'/>",&relation("notes.xml"),part)).unwrap_err().contains("预览限制"));
    }
    #[test]
    fn note_reference_alias_attributes_and_cross_role_targets_are_rejected(){
        let refs=format!("<w:footnoteReference xmlns:a='{W}' a:id='2' w:id='3'/>");let part=notes(&(note("2","TWO")+&note("3","THREE")));assert!(extract(&package(&refs,&relation("notes.xml"),part)).is_err(),"Same expanded attribute cannot choose the last prefix");
        let refs="<w:footnoteReference w:id='2'/><w:endnoteReference w:id='2'/>";let rows=relation("notes.xml")+&format!("<Relationship Id='e' Type='{R}/endnotes' Target='notes.xml'/>");assert!(extract(&package(refs,&rows,notes(&note("2","NOTE")))).is_err());
    }
    #[test]
    fn note_reference_empty_normal_note_is_not_replaced_by_invented_text(){
        let bytes=package("<w:footnoteReference w:id='2'/>",&relation("notes.xml"),notes("<w:footnote w:id='2'/>"));assert_eq!(extract(&bytes).unwrap(),"BODY[脚注 2]\n[脚注 2]\n");
    }
}

#[cfg(test)]
mod current_revision_tests {
    use super::*;
    use std::io::Write;
    const W:&str="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    const R:&str="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    fn zip(parts:&[(&str,String)])->Vec<u8>{let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));for(p,b)in parts{z.start_file(*p,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(b.as_bytes()).unwrap();}z.finish().unwrap().into_inner()}
    fn doc(content:&str,section:&str)->String{format!("<w:document xmlns:w='{W}' xmlns:r='{R}'><w:body><w:p>{content}</w:p>{section}</w:body></w:document>")}
    fn run(text:&str)->String{format!("<w:r><w:t>{text}</w:t></w:r>")}
    fn rels(rows:&str)->String{format!("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>{rows}</Relationships>")}
    #[test]
    fn revision_current_fixture_ignores_old_text_old_section_and_old_note(){
        let bytes=include_bytes!("../../../docs/review/20261019-windows-docx-current-revisions/current-revisions.docx");let expected="当前甲新增移入当前乙[脚注 2]\n当前页眉\n[脚注 2]\n脚注现文\n";
        assert_eq!(extract(bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(bytes,crate::attachment_document_text_v1::DOCX).unwrap(),expected);
    }
    #[test]
    fn revision_current_deleted_and_moved_source_skip_text_and_controls(){
        let content=format!("{}<w:moveFrom>{}<w:r><w:tab/><w:br/><w:noBreakHyphen/></w:r></w:moveFrom><w:del><w:ins>{}</w:ins></w:del><w:ins>{}</w:ins><w:moveTo>{}</w:moveTo>",run("BASE"),run("OLD_MOVE"),run("OLD_DELETE"),run("+INSERT"),run("+MOVED"));
        assert_eq!(extract(&zip(&[("word/document.xml",doc(&content,""))])).unwrap(),"BASE+INSERT+MOVED\n");
    }
    #[test]
    fn revision_current_note_projection_keeps_complete_namespace_scope(){
        let document=doc("<w:r><w:t>BODY</w:t><w:footnoteReference w:id='2'/></w:r>","<w:sectPr/>");
        let notes=format!("<w:footnotes xmlns:w='{W}' xmlns:c='urn:custom'><w:footnote w:id='2'><w:p><w:moveFrom>{}</w:moveFrom><c:moveFrom>{}</c:moveFrom>{}</w:p></w:footnote></w:footnotes>",run("OLD_NOTE"),run("FOREIGN_KEPT"),run("NOTE"));
        let relationships=rels(&format!("<Relationship Id='n' Type='{R}/footnotes' Target='footnotes.xml'/>"));let bytes=zip(&[("word/document.xml",document),("word/_rels/document.xml.rels",relationships),("word/footnotes.xml",notes)]);
        assert_eq!(extract(&bytes).unwrap(),"BODY[脚注 2]\n[脚注 2]\nFOREIGN_KEPTNOTE\n");
    }
    #[test]
    fn revision_current_old_section_does_not_reset_current_slot_validation(){
        let section="<w:sectPr><w:headerReference w:type='default' r:id='h'/><w:sectPrChange><w:sectPr><w:headerReference w:type='default' r:id='old'/></w:sectPr></w:sectPrChange><w:headerReference w:type='default' r:id='h'/></w:sectPr>";
        let relationships=rels(&format!("<Relationship Id='h' Type='{R}/header' Target='header.xml'/>"));let header=format!("<w:hdr xmlns:w='{W}'><w:p>{}</w:p></w:hdr>",run("HEADER"));
        assert!(extract(&zip(&[("word/document.xml",doc(&run("BODY"),section)),("word/_rels/document.xml.rels",relationships),("word/header.xml",header)])).is_err());
        assert!(extract(&zip(&[("word/document.xml",doc(&run("BODY"),"<w:sectPr><w:sectPr/></w:sectPr>"))])).is_err());
    }
    #[test]
    fn revision_current_same_local_foreign_element_does_not_remove_real_text(){
        let content=format!("<c:del xmlns:c='urn:custom'>{}</c:del><w:del>{}</w:del>",run("FOREIGN"),run("OLD"));let document=doc(&content,"").replace(W,"http://purl.oclc.org/ooxml/wordprocessingml/main");assert_eq!(extract(&zip(&[("word/document.xml",document)])).unwrap(),"FOREIGN\n");
    }
    #[test]
    fn revision_current_all_moved_source_is_not_a_fake_readable_body(){assert!(extract(&zip(&[("word/document.xml",doc(&format!("<w:moveFrom>{}</w:moveFrom>",run("OLD_ONLY")),""))])).is_err());}
    #[test]
    fn revision_current_property_snapshots_do_not_emit_old_characters(){
        for tag in ["rPrChange","pPrChange","sectPrChange","tblPrChange","trPrChange","tcPrChange","tblGridChange","tblPrExChange"]{
            let content=format!("{}<w:{tag}>{}</w:{tag}>{}",run("BEFORE"),run("OLD_PROP"),run("AFTER"));assert_eq!(extract(&zip(&[("word/document.xml",doc(&content,""))])).unwrap(),"BEFOREAFTER\n","{tag}");
        }
    }
}

#[cfg(test)]
mod table_revision_tests {
    use super::*;
    use std::io::Write;
    const W:&str="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    fn doc(body:&str)->Vec<u8>{let xml=format!("<w:document xmlns:w='{W}' xmlns:c='urn:custom'><w:body>{body}</w:body></w:document>");let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));z.start_file("word/document.xml",zip::write::SimpleFileOptions::default()).unwrap();z.write_all(xml.as_bytes()).unwrap();z.finish().unwrap().into_inner()}
    fn cell(text:&str,props:&str)->String{format!("<w:tc><w:tcPr>{props}</w:tcPr><w:p><w:r><w:t>{text}</w:t></w:r></w:p></w:tc>")}
    #[test]
    fn table_revision_deleted_row_and_cell_do_not_enter_current_material(){
        let body=format!("<w:tbl><w:tr>{}{}</w:tr><w:tr><w:trPr><w:del w:id='1'/></w:trPr>{}</w:tr><w:tr><w:trPr><w:ins w:id='2'/></w:trPr>{}</w:tr></w:tbl>",cell("CURRENT",""),cell("OLD_CELL","<w:cellDel w:id='3'/>"),cell("OLD_ROW",""),cell("INSERTED","<w:cellIns w:id='4'/>"));
        let bytes=doc(&body);assert_eq!(extract(&bytes).unwrap(),"CURRENT\nINSERTED\n");assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::DOCX).unwrap(),"CURRENT\nINSERTED\n");
    }
    #[test]
    fn table_revision_deleted_row_note_reference_is_not_resolved(){
        let body=format!("<w:tbl><w:tr><w:trPr><w:del w:id='1'></w:del></w:trPr><w:tc><w:p><w:r><w:footnoteReference w:id='99'/></w:r></w:p></w:tc></w:tr><w:tr>{}</w:tr></w:tbl>",cell("CURRENT",""));
        assert_eq!(extract(&doc(&body)).unwrap(),"CURRENT\n");
    }
    #[test]
    fn table_revision_nested_table_deletion_does_not_hide_outer_or_next_row(){
        let nested=format!("<w:tbl><w:tr><w:trPr><w:del w:id='1'/></w:trPr>{}</w:tr><w:tr>{}</w:tr></w:tbl>",cell("NESTED_OLD",""),cell("NESTED_CURRENT",""));
        let body=format!("<w:tbl><w:tr><w:tc><w:p><w:r><w:t>OUTER</w:t></w:r></w:p>{nested}<w:p><w:r><w:t>AFTER</w:t></w:r></w:p></w:tc>{}</w:tr></w:tbl>",cell("SIBLING",""));
        assert_eq!(extract(&doc(&body)).unwrap(),"OUTER\nNESTED_CURRENT\nAFTER\nSIBLING\n");
    }
    #[test]
    fn table_revision_foreign_and_historical_markers_keep_current_text(){
        let body=format!("<w:tbl><w:tr><w:trPr><c:del/><w:trPrChange><w:trPr><w:del/></w:trPr></w:trPrChange></w:trPr>{}</w:tr><w:tr>{}</w:tr></w:tbl>",cell("KEEP_A","<c:cellDel/><w:tcPrChange><w:tcPr><w:cellDel/></w:tcPr></w:tcPrChange>"),cell("KEEP_B",""));
        assert_eq!(extract(&doc(&body)).unwrap(),"KEEP_A\nKEEP_B\n");
        let mut z=zip::ZipArchive::new(Cursor::new(doc(&body))).unwrap();let mut xml=String::new();z.by_name("word/document.xml").unwrap().read_to_string(&mut xml).unwrap();let xml=xml.replace(W,"http://purl.oclc.org/ooxml/wordprocessingml/main");
        let mut output=zip::ZipWriter::new(Cursor::new(Vec::new()));output.start_file("word/document.xml",zip::write::SimpleFileOptions::default()).unwrap();output.write_all(xml.as_bytes()).unwrap();assert_eq!(extract(&output.finish().unwrap().into_inner()).unwrap(),"KEEP_A\nKEEP_B\n");
    }
    #[test]
    fn table_revision_late_property_cannot_silently_rewrite_emitted_material(){
        let body=format!("<w:tbl><w:tr>{}<w:trPr><w:del/></w:trPr></w:tr><w:tr>{}</w:tr></w:tbl>",cell("TOO_EARLY",""),cell("CURRENT",""));
        assert!(extract(&doc(&body)).is_err());
    }
    #[test]
    fn table_revision_full_body_header_and_note_fixture_keeps_original_current_text(){
        let bytes=include_bytes!("../../../docs/review/20261020-windows-docx-table-revisions/table-revisions.docx");
        let expected="正文甲\n当前格\n新增格\n嵌套现文\n正文乙[脚注 2]\n当前页眉格\n[脚注 2]\n当前脚注格\n";
        assert_eq!(extract(bytes).unwrap(),expected);
        assert_eq!(crate::attachment_document_text_v1::extract(bytes,crate::attachment_document_text_v1::DOCX).unwrap(),expected);
        assert!(extract(include_bytes!("../../../docs/review/20261020-windows-docx-table-revisions/invalid-table-revisions.docx")).unwrap_err().contains("修订属性晚于内容"));
    }
    #[test]
    fn table_revision_strict_namespace_and_paired_cell_marker_keep_exact_siblings(){
        let body=format!("<w:tbl><w:tr>{}{}</w:tr><w:tr><w:trPr><w:del/></w:trPr>{}</w:tr></w:tbl>",cell("KEPT",""),cell("OLD_CELL","<w:cellDel></w:cellDel>"),cell("OLD_ROW",""));
        let bytes=doc(&body);let mut z=zip::ZipArchive::new(Cursor::new(bytes)).unwrap();let mut xml=String::new();z.by_name("word/document.xml").unwrap().read_to_string(&mut xml).unwrap();let xml=xml.replace(W,"http://purl.oclc.org/ooxml/wordprocessingml/main");
        let mut output=zip::ZipWriter::new(Cursor::new(Vec::new()));output.start_file("word/document.xml",zip::write::SimpleFileOptions::default()).unwrap();output.write_all(xml.as_bytes()).unwrap();assert_eq!(extract(&output.finish().unwrap().into_inner()).unwrap(),"KEPT\n");
    }

}

#[cfg(test)]
mod paragraph_projection_tests {
    use super::*;
    use std::io::Write;
    const W:&str="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    const MARK:&str="<w:pPr><w:rPr><w:del/></w:rPr></w:pPr>";
    fn doc(body:&str)->Vec<u8>{let xml=format!("<w:document xmlns:w='{W}' xmlns:c='urn:foreign'><w:body>{body}</w:body></w:document>");let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));z.start_file("word/document.xml",zip::write::SimpleFileOptions::default()).unwrap();z.write_all(xml.as_bytes()).unwrap();z.finish().unwrap().into_inner()}
    fn p(text:&str,props:&str)->String{format!("<w:p>{props}<w:r><w:t>{text}</w:t></w:r></w:p>")}
    #[test]
    fn paragraph_projection_deleted_mark_joins_text_without_deleting_contents(){
        let body=format!("{}{}{}",p("A",MARK),p("B",""),p("C",""));
        let bytes=doc(&body);assert_eq!(extract(&bytes).unwrap(),"AB\nC\n");assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::DOCX).unwrap(),"AB\nC\n");
    }
    #[test]
    fn paragraph_projection_foreign_text_and_control_names_are_not_word_content(){
        let body="<w:p><w:r><w:t>A</w:t><c:t>FOREIGN</c:t><c:tab/><c:br/><c:cr/><c:noBreakHyphen/><c:softHyphen/><c:p></c:p><w:t>B</w:t></w:r></w:p>";
        assert_eq!(extract(&doc(body)).unwrap(),"AB\n");
    }
    #[test]
    fn paragraph_projection_historical_foreign_and_run_markers_do_not_merge_paragraphs(){
        for props in ["<w:pPr><w:rPr><c:del/></w:rPr></w:pPr>","<w:pPr><w:pPrChange><w:pPr><w:rPr><w:del/></w:rPr></w:pPr></w:pPrChange></w:pPr>","<w:r><w:rPr><w:del/></w:rPr></w:r>"]{
            assert_eq!(extract(&doc(&format!("{}{}",p("A",props),p("B","")))).unwrap(),"A\nB\n");
        }
    }
    #[test]
    fn paragraph_projection_strict_namespace_alias_and_paired_marker_are_owned(){
        for ns in [W,"http://purl.oclc.org/ooxml/wordprocessingml/main"]{
            let body=format!("{}{}",p("甲","<w:pPr><w:rPr><w:del></w:del></w:rPr></w:pPr>"),p("乙",""));
            let bytes=doc(&body);let mut z=zip::ZipArchive::new(Cursor::new(bytes)).unwrap();let mut xml=String::new();z.by_name("word/document.xml").unwrap().read_to_string(&mut xml).unwrap();xml=xml.replace(W,ns).replace("xmlns:w=","xmlns:q=").replace("w:","q:");
            let mut output=zip::ZipWriter::new(Cursor::new(Vec::new()));output.start_file("word/document.xml",zip::write::SimpleFileOptions::default()).unwrap();output.write_all(xml.as_bytes()).unwrap();assert_eq!(extract(&output.finish().unwrap().into_inner()).unwrap(),"甲乙\n");
        }
    }
    #[test]
    fn paragraph_projection_shared_body_header_and_note_keep_current_text(){
        let bytes=include_bytes!("../../../docs/review/20261020-windows-docx-paragraph-marks/paragraph-marks.docx");let expected="正文甲正文乙[脚注 2]\n页眉甲页眉乙\n[脚注 2]\n脚注甲脚注乙\n";
        assert_eq!(extract(bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(bytes,crate::attachment_document_text_v1::DOCX).unwrap(),expected);
    }
    #[test]
    fn paragraph_projection_late_revision_property_is_explicitly_rejected(){
        let bytes=include_bytes!("../../../docs/review/20261020-windows-docx-paragraph-marks/invalid-paragraph-marks.docx");assert!(extract(bytes).unwrap_err().contains("段落修订属性晚于内容"));
    }
    #[test]
    fn paragraph_projection_deleted_marks_do_not_join_independent_cells_or_tables(){
        let body=format!("<w:tbl><w:tr><w:tc>{}</w:tc><w:tc>{}</w:tc></w:tr></w:tbl>{}<w:tbl><w:tr><w:tc>{}</w:tc></w:tr></w:tbl>",p("A",MARK),p("B",""),p("C",MARK),p("D",""));
        assert_eq!(extract(&doc(&body)).unwrap(),"A\nB\nC\nD\n");
    }
    #[test]
    fn paragraph_projection_final_deleted_mark_and_explicit_line_break_keep_boundaries(){
        assert_eq!(extract(&doc(&p("A",MARK))).unwrap(),"A\n");
        let body=format!("<w:p>{MARK}<w:r><w:t>A</w:t><w:br/><w:t>B</w:t></w:r></w:p>{}",p("C",""));assert_eq!(extract(&doc(&body)).unwrap(),"A\nBC\n");
    }
    #[test]
    fn paragraph_projection_foreign_only_content_cannot_impersonate_word_text(){
        let bytes=doc("<c:p><c:r><c:t>FOREIGN_ONLY</c:t></c:r></c:p>");assert!(extract(&bytes).unwrap_err().contains("没有可读取的正文"));
    }
    #[test]
    fn paragraph_projection_empty_paragraph_ends_pending_join_without_changing_legacy_blank_policy(){
        for props in [MARK,""]{
            let body=format!("{}<w:p/>{}",p("A",props),p("B",""));assert_eq!(extract(&doc(&body)).unwrap(),"A\nB\n");
        }
    }
    #[test]
    fn paragraph_projection_skipped_text_cannot_hide_invalid_utf8(){
        for hidden in [b"<c:t><![CDATA[\xff]]></c:t>".as_slice(),b"<w:del><w:r><w:t><![CDATA[\xff]]></w:t></w:r></w:del>".as_slice(),b"<mc:AlternateContent><mc:Choice Requires='new'><w:r><w:t><![CDATA[\xff]]></w:t></w:r></mc:Choice><mc:Fallback><w:r><w:t>FALLBACK</w:t></w:r></mc:Fallback></mc:AlternateContent>".as_slice()]{
            let mut xml=b"<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' xmlns:c='urn:foreign' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:new='urn:unsupported'><w:body><w:p><w:r><w:t>CURRENT</w:t></w:r>".to_vec();xml.extend_from_slice(hidden);xml.extend_from_slice(b"</w:p></w:body></w:document>");
            let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));z.start_file("word/document.xml",zip::write::SimpleFileOptions::default()).unwrap();z.write_all(&xml).unwrap();let bytes=z.finish().unwrap().into_inner();
            assert!(extract(&bytes).unwrap_err().contains("文本编码"));assert!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::DOCX).unwrap_err().contains("编码"));
        }
    }
}
