use crate::office_xml_compatibility_v1::{OfficeCompatibility, OfficeTextFormat};
use quick_xml::{events::Event, name::ResolveResult, NsReader};
use std::collections::{HashMap, HashSet};
use std::io::{Cursor, Read, Seek};

const MAX_ENTRIES: usize = 2_000;
const MAX_PART_BYTES: usize = 4 * 1024 * 1024;
const MAX_SELECTED_BYTES: usize = 12 * 1024 * 1024;
const MAX_OUTPUT_BYTES: usize = 2 * 1024 * 1024;

fn slide_number(path: &str) -> Option<u32> {
    let number = path
        .strip_prefix("ppt/slides/slide")?
        .strip_suffix(".xml")?;
    (!number.is_empty() && number.bytes().all(|byte| byte.is_ascii_digit()))
        .then(|| number.parse().ok())
        .flatten()
}

fn append_text(line: &mut String, value: &str, output_bytes: &mut usize) -> Result<(), String> {
    *output_bytes += value.len();
    if *output_bytes > MAX_OUTPUT_BYTES { return Err("PPTX 文本超过本机投影上限".into()); }
    line.push_str(value);
    Ok(())
}

fn drawing_namespace(ns: &ResolveResult<'_>) -> bool {
    matches!(ns, ResolveResult::Bound(n) if matches!(n.as_ref(),
        b"http://schemas.openxmlformats.org/drawingml/2006/main" |
        b"http://purl.oclc.org/ooxml/drawingml/main"))
}

fn slide_text(xml: &[u8]) -> Result<String, String> {
    let mut reader = NsReader::from_reader(xml);
    let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Presentation);
    let mut lines = Vec::new();
    let mut line = String::new();
    let mut depth = 0usize;
    let mut paragraph_depth = None;
    let mut text_depth = None;
    let mut output_bytes = 0;
    loop {
        let(ns,event)=reader.read_resolved_event().map_err(|_| "PPTX 页面 XML 无效")?;
        let owned = drawing_namespace(&ns);
        let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}
        match event {
            Event::DocType(_) => return Err("PPTX 不允许外部实体".into()),
            Event::Start(event) => {
                if owned { match event.local_name().as_ref() {
                    b"p" => {
                        if paragraph_depth.is_some() { return Err("PPTX 正文段落嵌套无效".into()); }
                        paragraph_depth = Some(depth + 1);
                        line.clear();
                    }
                    b"t" if paragraph_depth.is_some() => {
                        if text_depth.is_some() { return Err("PPTX 正文文字嵌套无效".into()); }
                        text_depth = Some(depth + 1);
                    }
                    b"br" if paragraph_depth.is_some() => append_text(&mut line, "\n", &mut output_bytes)?,
                    _ => {}
                }}
                depth += 1;
            }
            Event::Text(event) if text_depth == Some(depth) => {
                let value = event.unescape().map_err(|_| "PPTX 文字编码无效")?;
                append_text(&mut line, &value, &mut output_bytes)?;
            }
            Event::CData(event) if text_depth == Some(depth) => {
                let value = std::str::from_utf8(event.as_ref()).map_err(|_| "PPTX 文字编码无效")?;
                append_text(&mut line, value, &mut output_bytes)?;
            }
            Event::Empty(event) if owned && paragraph_depth.is_some() && event.local_name().as_ref() == b"br" =>
                append_text(&mut line, "\n", &mut output_bytes)?,
            Event::End(event) => {
                if owned { match event.local_name().as_ref() {
                b"t" if text_depth == Some(depth) => text_depth = None,
                b"p" if paragraph_depth == Some(depth) => {
                    if !line.trim().is_empty() {
                        // Paragraph separators and explicit breaks share the text budget.
                        output_bytes += usize::from(!lines.is_empty());
                        if output_bytes > MAX_OUTPUT_BYTES { return Err("PPTX 文本超过本机投影上限".into()); }
                        lines.push(std::mem::take(&mut line));
                    }
                    paragraph_depth = None;
                }
                _ => {}
                }}
                depth = depth.checked_sub(1).ok_or("PPTX 页面 XML 无效")?;
            }
            Event::Eof => {
                if depth != 0 { return Err("PPTX 页面 XML 不完整".into()); }
                break;
            }
            _ => {}
        }
    }
    Ok(lines.join("\n"))
}

fn read_part<R: Read + Seek>(zip: &mut zip::ZipArchive<R>, path: &str, selected: &mut usize) -> Result<Vec<u8>, String> {
    let item=zip.by_name(path).map_err(|_| "PPTX 引用的部件不可读")?;
    let mut xml=Vec::new();
    item.take(MAX_PART_BYTES as u64+1).read_to_end(&mut xml).map_err(|_| "PPTX 部件读取失败")?;
    *selected+=xml.len();
    if xml.len()>MAX_PART_BYTES || *selected>MAX_SELECTED_BYTES {return Err("PPTX XML 超出本机解析上限".into());}
    // Validate the whole bounded part before namespace/compatibility filtering;
    // skipped foreign text is still part of the declared UTF-8 XML document.
    std::str::from_utf8(&xml).map_err(|_| "PPTX 部件文字编码无效")?;
    Ok(xml)
}

fn presentation_namespace(ns: &ResolveResult<'_>) -> bool {
    matches!(ns,ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/presentationml/2006/main"|b"http://purl.oclc.org/ooxml/presentationml/main"))
}

fn relationship_namespace(ns: &ResolveResult<'_>) -> bool {
    matches!(ns,ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/officeDocument/2006/relationships"|b"http://purl.oclc.org/ooxml/officeDocument/relationships"))
}

fn slide_ids(xml: &[u8]) -> Result<Vec<String>,String> {
    let mut reader=NsReader::from_reader(xml);let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Presentation);let mut depth=0usize;let mut list=false;let mut seen_list=false;
    let mut ids=Vec::new();let mut seen=HashSet::new();
    loop {
        let (ns,event)=reader.read_resolved_event().map_err(|_| "PPTX 页面顺序 XML 无效")?;
        let owned=presentation_namespace(&ns);
        let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}
        match event {
            Event::DocType(_)=>return Err("PPTX 不允许外部实体".into()),
            Event::Start(ref e)|Event::Empty(ref e)=>{
                let name=e.local_name();
                if depth==0 && (!owned || name.as_ref()!=b"presentation") {return Err("PPTX 页面顺序根无效".into());}
                if depth==1 && owned && name.as_ref()==b"sldIdLst" {
                    if seen_list {return Err("PPTX 页面顺序重复".into());}seen_list=true;list=true;
                }
                if list && depth==2 && owned && name.as_ref()==b"sldId" {
                    let mut id=None;
                    for a in e.attributes() {
                        let a=a.map_err(|_| "PPTX 页面关系属性无效")?;
                        let (ns,key)=reader.resolve_attribute(a.key);
                        if relationship_namespace(&ns) && key.as_ref()==b"id" {
                            if id.is_some(){return Err("PPTX 页面关系歧义".into());}
                            id=Some(a.decode_and_unescape_value(&reader).map_err(|_| "PPTX 页面关系编码无效")?.into_owned());
                        }
                    }
                    let id=id.filter(|v|!v.is_empty()).ok_or("PPTX 页面缺少关系")?;
                    if !seen.insert(id.clone()){return Err("PPTX 页面引用重复".into());}ids.push(id);
                }
                if matches!(event,Event::Start(_)){depth+=1;}else if depth==1 && owned && name.as_ref()==b"sldIdLst"{list=false;}
            }
            Event::End(e)=>{depth=depth.checked_sub(1).ok_or("PPTX 页面顺序 XML 无效")?;if depth==1 && e.local_name().as_ref()==b"sldIdLst"{list=false;}}
            Event::Eof=>{if depth!=0{return Err("PPTX 页面顺序 XML 不完整".into());}break;}
            _=>{}
        }
    }
    if ids.is_empty(){return Err("PPTX 没有可读取的页面".into());}Ok(ids)
}

fn internal_slide_path(target: &str) -> Result<String,String> {
    // Resolve only package parts. Never access a filesystem or follow external relationships.
    if target.is_empty() || target.contains(['\\',':','?','#','%']) || target.starts_with("//") {return Err("PPTX 页面目标不受支持".into());}
    let mut parts=if target.starts_with('/') {Vec::new()}else{vec!["ppt"]};
    for part in target.split('/') {
        match part {""|"."=>{},".."=>{parts.pop().ok_or("PPTX 页面目标越界")?;},value=>parts.push(value)}
    }
    let path=parts.join("/");if !path.ends_with(".xml"){return Err("PPTX 页面目标无效".into());}Ok(path)
}

fn ordered_slide_paths(presentation: &[u8], relationships: &[u8]) -> Result<Vec<String>,String> {
    let ids=slide_ids(presentation)?;let mut reader=NsReader::from_reader(relationships);let mut depth=0usize;
    let mut rels=HashMap::new();
    loop {
        let (ns,event)=reader.read_resolved_event().map_err(|_| "PPTX 关系 XML 无效")?;
        let owned=matches!(ns,ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/package/2006/relationships"|b"http://purl.oclc.org/ooxml/package/relationships"));
        match event {
            Event::DocType(_)=>return Err("PPTX 不允许外部实体".into()),
            Event::Start(ref e)|Event::Empty(ref e)=>{
                if depth==0 && (!owned || e.local_name().as_ref()!=b"Relationships"){return Err("PPTX 关系根无效".into());}
                if depth==1 && owned && e.local_name().as_ref()==b"Relationship" {
                    let mut attrs=HashMap::new();
                    for a in e.attributes(){let a=a.map_err(|_| "PPTX 关系属性无效")?;attrs.insert(a.key.as_ref().to_vec(),a.decode_and_unescape_value(&reader).map_err(|_| "PPTX 关系编码无效")?.into_owned());}
                    let id=attrs.remove(b"Id".as_slice()).filter(|s|!s.is_empty()).ok_or("PPTX 关系缺少身份")?;
                    if rels.insert(id,attrs).is_some(){return Err("PPTX 关系身份重复".into());}
                }
                if matches!(event,Event::Start(_)){depth+=1;}
            }
            Event::End(_)=>depth=depth.checked_sub(1).ok_or("PPTX 关系 XML 无效")?,
            Event::Eof=>{if depth!=0{return Err("PPTX 关系 XML 不完整".into());}break;}
            _=>{}
        }
    }
    let mut paths=Vec::new();let mut seen=HashSet::new();
    for id in ids {
        let rel=rels.get(&id).ok_or("PPTX 页面关系缺失")?;
        if !matches!(rel.get(b"Type".as_slice()).map(String::as_str),Some("http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"|"http://purl.oclc.org/ooxml/officeDocument/relationships/slide")) {return Err("PPTX 页面关系类型无效".into());}
        if rel.get(b"TargetMode".as_slice()).is_some_and(|m|m!="Internal"){return Err("PPTX 不读取外部页面".into());}
        let path=internal_slide_path(rel.get(b"Target".as_slice()).ok_or("PPTX 页面目标缺失")?)?;
        if !seen.insert(path.clone()){return Err("PPTX 页面目标重复".into());}paths.push(path);
    }
    Ok(paths)
}

pub fn extract(bytes: &[u8]) -> Result<String, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "PPTX 文件结构无效")?;
    crate::attachment_document_text_v1::validate_raw_directory(bytes, zip.central_directory_start(), zip.len())?;
    if zip.len() > MAX_ENTRIES {
        return Err("PPTX 条目超过本机解析上限".into());
    }
    let mut slides = Vec::new();let mut has_presentation=false;let mut has_relationships=false;
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "PPTX 条目不可读")?;
        if item.enclosed_name().is_none() {
            return Err("PPTX 包含不安全路径".into());
        }
        if item.is_dir() && item.size() != 0 {
            return Err("PPTX 目录条目含异常内容".into());
        }
        has_presentation|=item.name()=="ppt/presentation.xml";
        has_relationships|=item.name()=="ppt/_rels/presentation.xml.rels";
        if let Some(number) = slide_number(item.name()) {
            slides.push((number, item.name().to_owned()));
        }
    }
    slides.sort_by_key(|(number, _)| *number);
    let mut selected_bytes = 0;
    let slides:Vec<(usize,String)>=match (has_presentation,has_relationships) {
        (true,true)=>{
            let p=read_part(&mut zip,"ppt/presentation.xml",&mut selected_bytes)?;
            let r=read_part(&mut zip,"ppt/_rels/presentation.xml.rels",&mut selected_bytes)?;
            ordered_slide_paths(&p,&r)?.into_iter().enumerate().map(|(i,p)|(i+1,p)).collect()
        }
        (false,false)=>slides.into_iter().map(|(n,p)|(n as usize,p)).collect(),
        _=>return Err("PPTX 页面顺序元数据不完整".into()),
    };
    if slides.is_empty(){return Err("PPTX 没有可读取的页面".into());}
    let mut output_bytes = 0;
    let mut output = Vec::new();
    for (number, path) in slides {
        let xml=read_part(&mut zip,&path,&mut selected_bytes)?;
        let text = slide_text(&xml)?;
        if !text.trim().is_empty() {
            output_bytes += text.len();
            if output_bytes > MAX_OUTPUT_BYTES {
                return Err("PPTX 文本超过本机投影上限".into());
            }
            output.push(format!("第 {number} 页\n{text}"));
        }
    }
    if output.is_empty() {
        return Err("PPTX 没有可读取的正文".into());
    }
    Ok(output.join("\n\n"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn presentation(slide: &str) -> Vec<u8> {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file(
                "ppt/slides/slide1.xml",
                zip::write::SimpleFileOptions::default(),
            )
            .unwrap();
        writer.write_all(slide.as_bytes()).unwrap();
        writer.finish().unwrap().into_inner()
    }



    #[test]
    fn text_namespace_foreign_paragraphs_do_not_clear_or_end_drawing_text() {
        let xml="<p:sld xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' xmlns:x='urn:foreign'><x:p><x:t>FOREIGN_OUTSIDE</x:t></x:p><a:p><a:r><a:t>保留甲</a:t></a:r><x:p><x:t>FOREIGN_PARAGRAPH</x:t></x:p><a:r><a:t>保留乙</a:t></a:r><x:br/><a:br/><a:r><a:t>末行</a:t></a:r></a:p></p:sld>";
        let bytes=presentation(xml);let expected="第 1 页\n保留甲保留乙\n末行";
        assert_eq!(extract(&bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::PPTX).unwrap(),expected);
    }

    #[test]
    fn text_namespace_foreign_text_child_does_not_inject_or_end_own_text() {
        let xml="<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' xmlns:x='urn:foreign'><a:r><a:t>开始<x:t>FOREIGN_CHILD</x:t>结尾<x:t/>仍在</a:t></a:r><x:t>FOREIGN_SIBLING</x:t><a:r><a:t>原文</a:t></a:r></a:p>";
        assert_eq!(extract(&presentation(xml)).unwrap(),"第 1 页\n开始结尾仍在原文");
    }

    #[test]
    fn text_namespace_strict_alias_default_and_cdata_remain_readable() {
        let xml="<s><d:p xmlns:d='http://purl.oclc.org/ooxml/drawingml/main'><d:r><d:t>STRICT &amp; <![CDATA[<原文>]]></d:t></d:r><d:br/><d:r><d:t>换行</d:t></d:r></d:p><p xmlns='http://schemas.openxmlformats.org/drawingml/2006/main'><r><t>DEFAULT</t></r></p></s>";
        assert_eq!(extract(&presentation(xml)).unwrap(),"第 1 页\nSTRICT & <原文>\n换行\nDEFAULT");
    }


    #[test]
    fn text_namespace_prefix_rebinding_does_not_use_the_familiar_prefix_as_identity() {
        let xml="<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>BEFORE</a:t></a:r><a:t xmlns:a='urn:foreign'>FOREIGN_REBOUND</a:t><a:r><a:t>AFTER</a:t></a:r></a:p>";
        assert_eq!(extract(&presentation(xml)).unwrap(),"第 1 页\nBEFOREAFTER");
    }

    #[test]
    fn text_namespace_ambiguous_nested_or_unclosed_drawing_text_is_rejected() {
        for content in ["<a:p><a:r><a:t>outer</a:t></a:r><a:p><a:r><a:t>inner</a:t></a:r></a:p></a:p></s>",
            "<a:p><a:r><a:t>outer<a:t>inner</a:t></a:t></a:r></a:p></s>",
            "<a:p><a:r><a:t>partial</a:t></a:r></a:p>"] {
            let xml=format!("<s xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'>{content}");
            assert!(extract(&presentation(&xml)).is_err());
        }
    }

    #[test]
    fn text_namespace_foreign_only_is_not_readable_presentation_text() {
        assert!(extract(&presentation("<x:p xmlns:x='urn:foreign'><x:t>FOREIGN_ONLY</x:t></x:p>")).unwrap_err().contains("没有可读取的正文"));
    }

    #[test]
    fn text_namespace_invalid_utf8_is_rejected_even_in_foreign_or_unselected_content() {
        for xml in [
            b"<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' xmlns:x='urn:foreign'><a:r><a:t>good</a:t></a:r><x:t>\xff</x:t></a:p>".as_slice(),
            b"<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:x='urn:foreign'><mc:AlternateContent><mc:Choice Requires='x'><a:r><a:t>\xff</a:t></a:r></mc:Choice><mc:Fallback><a:r><a:t>good</a:t></a:r></mc:Fallback></mc:AlternateContent></a:p>".as_slice(),
        ] {
            let mut writer=zip::ZipWriter::new(Cursor::new(Vec::new()));writer.start_file("ppt/slides/slide1.xml",zip::write::SimpleFileOptions::default()).unwrap();writer.write_all(xml).unwrap();let bytes=writer.finish().unwrap().into_inner();
            assert!(extract(&bytes).unwrap_err().contains("编码"));
        }
    }

    #[test] fn office_alternatives_slide_text_uses_selected_choice_or_fallback_without_double_text(){
        let xml="<p:sld xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><a:p><a:r><a:t>BEFORE_</a:t></a:r><mc:AlternateContent><mc:Choice Requires='w'><a:r><a:t>UNSELECTED_WORD</a:t></a:r></mc:Choice><mc:Fallback xmlns:q='http://purl.oclc.org/ooxml/drawingml/main'><q:r><q:t><![CDATA[FALLBACK & 原文]]></q:t></q:r><mc:AlternateContent><mc:Choice Requires='a'><a:r><a:t>_NESTED</a:t></a:r></mc:Choice><mc:Fallback><a:r><a:t>UNSELECTED_NESTED</a:t></a:r></mc:Fallback></mc:AlternateContent></mc:Fallback></mc:AlternateContent></a:p></p:sld>";
        let bytes=presentation(xml);let expected="第 1 页\nBEFORE_FALLBACK & 原文_NESTED";assert_eq!(extract(&bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::PPTX).unwrap(),expected);
    }
    #[test] fn office_alternatives_presentation_manifest_keeps_selected_order_and_ignores_missing_relation(){
        let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for(path,xml)in [("ppt/presentation.xml","<p:presentation xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:new='urn:synthetic-new'><mc:AlternateContent><mc:Choice Requires='new'><p:sldIdLst><p:sldId r:id='absent'/></p:sldIdLst></mc:Choice><mc:Fallback><p:sldIdLst><p:sldId r:id='second'/><p:sldId r:id='first'/></p:sldIdLst></mc:Fallback></mc:AlternateContent></p:presentation>"),("ppt/_rels/presentation.xml.rels","<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='first' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/slide1.xml'/><Relationship Id='second' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/slide2.xml'/></Relationships>"),("ppt/slides/slide1.xml","<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>FIRST_FILE</a:t></a:r></a:p></p:sld>"),("ppt/slides/slide2.xml","<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>SECOND_FILE</a:t></a:r></a:p></p:sld>")]{z.start_file(path,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(xml.as_bytes()).unwrap();}
        assert_eq!(extract(&z.finish().unwrap().into_inner()).unwrap(),"第 1 页\nSECOND_FILE\n\n第 2 页\nFIRST_FILE");
    }
    #[test] fn office_alternatives_pptx_malformed_requires_is_not_read_as_normal_content(){
        let xml="<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'><a:p><mc:AlternateContent><mc:Choice><a:r><a:t>UNSELECTED</a:t></a:r></mc:Choice><mc:Fallback><a:r><a:t>FALLBACK</a:t></a:r></mc:Fallback></mc:AlternateContent></a:p></p:sld>";assert!(extract(&presentation(xml)).is_err());
    }
    #[test]
    fn mixed_cdata_runs_preserve_literal_text_and_paragraphs() {
        let bytes = presentation("<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>前 &amp; <![CDATA[<页面>&amp;😀]]> 后</a:t></a:r></a:p><a:p><a:r><a:t><![CDATA[完整末尾]]></a:t></a:r></a:p></p:sld>");
        assert_eq!(extract(&bytes).unwrap(), "第 1 页\n前 & <页面>&amp;😀 后\n完整末尾");
    }
    #[test]
    fn extracts_slide_paragraphs_without_interpreting_actions() {
        let bytes = presentation("<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>标题 &amp; 内容</a:t></a:r></a:p><a:p><a:r><a:t>第二行</a:t></a:r></a:p></p:sld>");
        assert_eq!(extract(&bytes).unwrap(), "第 1 页\n标题 & 内容\n第二行");
    }

    #[test]
    fn rejects_invalid_or_external_entity_slides() {
        assert!(extract(&presentation("<!DOCTYPE x SYSTEM 'file:///secret'><x/>")).is_err());
        assert!(extract(&presentation("<p:sld/>")).is_err());
        assert!(extract(b"not a presentation").is_err());
    }
    #[test]
    fn explicit_soft_breaks_separate_material_lines_without_splitting_plain_runs() {
        let bytes = presentation("<p:sld xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:br/><a:p><a:r><a:t>原始</a:t></a:r><a:r><a:t>材料</a:t></a:r></a:p><a:p><a:r><a:t>收入100</a:t></a:r><a:br/><a:r><a:t>成本80</a:t></a:r><a:br><a:rPr lang='zh-CN'/></a:br><a:br/><a:r><a:t>差额20</a:t></a:r></a:p></p:sld>");
        let expected = "第 1 页\n原始材料\n收入100\n成本80\n\n差额20";
        assert_eq!(extract(&bytes).unwrap(), expected);
        assert_eq!(crate::attachment_document_text_v1::extract(&bytes, crate::attachment_document_text_v1::PPTX).unwrap(), expected);
    }

    #[test]
    fn explicit_breaks_and_paragraph_separators_share_the_existing_output_limit() {
        let text = "x".repeat(MAX_OUTPUT_BYTES - 2);
        let xml = format!("<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>{text}</a:t></a:r><a:br/><a:br/><a:br/></a:p>");
        assert!(extract(&presentation(&xml)).unwrap_err().contains("投影上限"));
        let xml = format!("<s xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>{text}x</a:t></a:r></a:p><a:p><a:r><a:t>x</a:t></a:r></a:p></s>");
        assert!(extract(&presentation(&xml)).unwrap_err().contains("投影上限"));
    }

    fn deck_with_manifest(manifest: &str, rels: &str, slides: &[(&str,&str)]) -> Vec<u8> {
        let mut writer=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (name,text) in [("ppt/presentation.xml",manifest),("ppt/_rels/presentation.xml.rels",rels)].into_iter().chain(slides.iter().copied()) {
            writer.start_file(name,zip::write::SimpleFileOptions::default()).unwrap();writer.write_all(text.as_bytes()).unwrap();
        }
        writer.finish().unwrap().into_inner()
    }
    #[test]
    fn presentation_order_and_membership_reach_preview_and_material() {
        let bytes=deck_with_manifest(
            "<p:presentation xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><p:sldIdLst><p:sldId id='256' r:id='second'/><p:sldId id='257' r:id='first'/></p:sldIdLst></p:presentation>",
            "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='first' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/slide1.xml'/><Relationship Id='second' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/slide2.xml'/></Relationships>",
            &[("ppt/slides/slide1.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>后读事项</a:t></a:r></a:p>"),("ppt/slides/slide2.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>先读事项</a:t></a:r></a:p>"),("ppt/slides/slide9.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>未引用页面</a:t></a:r></a:p>")]);
        let expected="第 1 页\n先读事项\n\n第 2 页\n后读事项";
        assert_eq!(extract(&bytes).unwrap(),expected);
        assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::PPTX).unwrap(),expected);
    }
    #[test]
    fn renamed_slide_parts_and_strict_namespace_aliases_keep_document_order() {
        let bytes=deck_with_manifest(
            "<q:presentation xmlns:q='http://purl.oclc.org/ooxml/presentationml/main' xmlns:link='http://purl.oclc.org/ooxml/officeDocument/relationships'><q:sldIdLst><q:sldId id='900' link:id='renamed'></q:sldId></q:sldIdLst></q:presentation>",
            "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='renamed' Type='http://purl.oclc.org/ooxml/officeDocument/relationships/slide' Target='/ppt/slides/renamed.xml'></Relationship></Relationships>",
            &[("ppt/slides/renamed.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>非编号原页面</a:t></a:r></a:p>")]);
        assert_eq!(extract(&bytes).unwrap(),"第 1 页\n非编号原页面");
    }
    #[test]
    fn broken_or_external_slide_references_never_fall_back_to_physical_files() {
        let manifest="<p:presentation xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><p:sldIdLst><p:sldId id='256' r:id='a'/></p:sldIdLst></p:presentation>";
        let slide="<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>禁止错误兜底</a:t></a:r></a:p>";
        for relationship in [
            "",
            "<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/missing.xml'/>",
            "<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='https://example.test/slide.xml' TargetMode='External'/>",
            "<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='../../outside.xml'/>",
            "<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide' Target='slides/slide1.xml'/>",
            "<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/slide1.xml'/><Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/slide1.xml'/>",
        ] {
            let rels=format!("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>{relationship}</Relationships>");
            let bytes=deck_with_manifest(manifest,&rels,&[("ppt/slides/slide1.xml",slide)]);
            assert!(extract(&bytes).is_err(),"{relationship}");
            assert!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::PPTX).is_err());
        }
        let bytes=deck_with_manifest(manifest,"<!DOCTYPE x SYSTEM 'file:///private'><Relationships/>",&[("ppt/slides/slide1.xml",slide)]);
        assert!(extract(&bytes).is_err());
        let mut archive=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (name,text) in [("ppt/presentation.xml",manifest),("ppt/slides/slide1.xml",slide)] {
            archive.start_file(name,zip::write::SimpleFileOptions::default()).unwrap();archive.write_all(text.as_bytes()).unwrap();
        }
        assert!(extract(&archive.finish().unwrap().into_inner()).is_err());
    }
    #[test]
    fn duplicate_slide_membership_and_oversized_order_metadata_are_rejected() {
        let manifest="<p:presentation xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><p:sldIdLst><p:sldId id='256' r:id='a'/><p:sldId id='257' r:id='a'/></p:sldIdLst></p:presentation>";
        let rels="<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/slide1.xml'/></Relationships>";
        let slides=[("ppt/slides/slide1.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>原文</a:t></a:r></a:p>")];
        assert!(extract(&deck_with_manifest(manifest,rels,&slides)).is_err());
        let oversized=format!("{manifest}<!--{}-->","x".repeat(MAX_PART_BYTES));
        assert!(extract(&deck_with_manifest(&oversized,rels,&slides)).unwrap_err().contains("解析上限"));
    }

    #[test]
    fn pptx_attribute_entities_reach_the_exact_referenced_parts() {
        let bytes=include_bytes!("../../../docs/review/20261018-windows-pptx-attribute-entities/attribute-entities.pptx");
        let expected="第 1 页\n公开事项1：机构在2026年发布第一份公告。\n原文 A &amp; B\n\n第 2 页\n公开事项2：机构在2026年发布第二份公告。";
        assert_eq!(extract(bytes).unwrap(),expected);
        assert_eq!(crate::attachment_document_text_v1::extract(bytes,crate::attachment_document_text_v1::PPTX).unwrap(),expected);
    }
    #[test]
    fn attribute_target_entities_are_decoded_once_without_rewriting_literal_part_names() {
        let manifest="<p:presentation xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><p:sldIdLst><p:sldId id='256' r:id='rId1'/></p:sldIdLst></p:presentation>";
        let rels="<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='rId1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/a&amp;amp;b.xml'/></Relationships>";
        let bytes=deck_with_manifest(manifest,rels,&[("ppt/slides/a&amp;b.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>实际原部件</a:t></a:r></a:p>"),("ppt/slides/a&b.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>不能二次解码误选</a:t></a:r></a:p>")]);
        assert_eq!(extract(&bytes).unwrap(),"第 1 页\n实际原部件");
    }
    #[test]
    fn unknown_attribute_entities_still_reject_instead_of_guessing_part_names() {
        let manifest="<p:presentation xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><p:sldIdLst><p:sldId id='256' r:id='rId1'/></p:sldIdLst></p:presentation>";
        let rels="<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='rId1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide' Target='slides/a&custom;b.xml'/></Relationships>";
        assert!(extract(&deck_with_manifest(manifest,rels,&[("ppt/slides/a&custom;b.xml","<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>不可猜选</a:t></a:r></a:p>")])).is_err());
    }

}
