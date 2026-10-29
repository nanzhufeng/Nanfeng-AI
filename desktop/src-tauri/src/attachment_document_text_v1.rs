//! Local full-document projection shared by ordinary chat and its fact-search plan.
use std::io::{Cursor, Read};

pub const DOCX: &str = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
pub const XLSX: &str = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
pub const PPTX: &str = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
pub fn supports(mime: &str) -> bool {
 static TYPES: std::sync::LazyLock<Vec<String>> = std::sync::LazyLock::new(|| {
  serde_json::from_str::<serde_json::Value>(include_str!("../../../protocol/fixtures/attachment-document-types-v1.json")).expect("owned document types")["mimeTypes"].as_array().expect("owned MIME list").iter().map(|m|m.as_str().expect("owned MIME").to_owned()).collect()
 });
 mime == "application/zip" || TYPES.iter().any(|m|m==mime)
}
pub fn pdf_projection(bytes:&[u8])->Result<Option<String>,String> {
    let document=lopdf::Document::load_mem(bytes).map_err(|_| "PDF 结构无法完整读取")?;
    if document.is_encrypted() {return Err("加密 PDF 无法读取".into())}
    let pages=document.get_pages();
    if pages.is_empty() || pages.len()>2000 {return Err("PDF 页数超出完整投影限制".into())}
    let mut text=String::new();let mut needs_reading=false;
    for (page,id) in pages {
        let body=document.extract_text(&[page]).unwrap_or_default();
        let xobjects=document.get_page_resources(id).map(|(resources,ids)| {
            resources.is_some_and(|r|r.has(b"XObject")) || ids.iter().any(|id|document.get_dictionary(*id).is_ok_and(|r|r.has(b"XObject")))
        }).unwrap_or(true);
        let drawings=document.get_page_content(id).ok().and_then(|bytes|lopdf::content::Content::decode(&bytes).ok()).is_none_or(|content|content.operations.iter().any(|op|matches!(op.operator.as_str(),"S"|"s"|"f"|"F"|"f*"|"B"|"B*"|"b"|"b*"|"sh"|"Do")));
        if body.trim().is_empty() || xobjects || drawings {needs_reading=true;}
        text.push_str(&body);text.push('\n');
        if text.len()>3_200_000 || text.chars().count()>800_000 {return Err("PDF 可读文本超出完整投影限制".into())}
    }
    Ok(if needs_reading {None}else{Some(pdf_text(bytes)?)})
}
pub fn pdf_text(bytes: &[u8]) -> Result<String, String> {
    let mut document=lopdf::Document::load_mem(bytes).map_err(|_| "PDF 文本层无法读取")?;
    if document.is_encrypted() { return Err("加密 PDF 无法本机提取正文".into()); }
    let pages=document.get_pages();
    if pages.len()>2000 { return Err("PDF 页数超出本机提取上限".into()); }
    let mut text=String::new();
    for (page,page_id) in pages {
        let content=document.get_page_content(page_id).map_err(|_| "PDF 页面内容无法读取")?;
        let projected=crate::desktop_pdf_text_layout_v1::positioned_text_projection(&content)
            .map_err(|_| "PDF 文本行边界无法完整读取")?;
        let stream=document.add_object(lopdf::Stream::new(lopdf::Dictionary::new(),projected));
        document.get_object_mut(page_id).and_then(lopdf::Object::as_dict_mut)
            .map_err(|_| "PDF 页面无法读取")?.set("Contents",stream);
        text.push_str(&document.extract_text(&[page]).map_err(|_| "PDF 文本层无法完整读取")?);
        text.push('\n');
        if text.len()>3_200_000 { return Err("PDF 可读文本超出完整投影限制".into()); }
    }
    if text.trim().is_empty() { return Err("PDF 没有可读取的文本层，请先使用附件转写".into()); }
    if text.chars().count()>800_000 { return Err("PDF 可读文本超出完整投影限制".into()); }
    Ok(text)
}
/// Only source labels are escaped. Original metadata, bytes and body line breaks stay intact.
pub fn material_header(name:&str)->String {
    let mut value=String::from("### 附件：");
    for ch in name.chars() {
        match ch {
            '\n'=>value.push_str("\\n"), '\r'=>value.push_str("\\r"), '\t'=>value.push_str("\\t"),
            '\\'=>value.push_str("\\\\"),
            c if c.is_control() || matches!(c,'\u{2028}'|'\u{2029}')=>value.push_str(&format!("\\u{:04X}",c as u32)),
            c=>value.push(c),
        }
    }
    value
}
pub fn extract(bytes: &[u8], mime: &str) -> Result<String, String> {
    if !supports(mime) { return Err("附件不是受支持的文本格式".into()); }
    let text = match mime {
        "application/zip" => archive_text(bytes)?,
        DOCX => crate::desktop_docx_preview::extract(bytes)?,
        XLSX => crate::desktop_xlsx_preview::extract(bytes)?,
        PPTX => crate::desktop_pptx_preview::extract(bytes)?,
        _ => {
            if bytes.len() > 2 * 1024 * 1024 { return Err("文本附件超过 2 MB 投影上限".into()); }
            if bytes.contains(&0) {return Err("文本附件含二进制空字节".into());}
            std::str::from_utf8(bytes).map_err(|_| "文本附件不是 UTF-8")?.into()
        }
    };
    if matches!(mime,DOCX|XLSX|PPTX) && text.len()>2*1024*1024 { return Err("Office 文本超过 2 MB 投影上限".into()); }
    if text.chars().count() > 800_000 { return Err("附件可读文本超过完整投影上限，未截断后发送".into()); }
    Ok(text)
}
pub(crate) fn validate_raw_directory(bytes: &[u8], start: u64, visible_count: usize) -> Result<(), String> {
    let mut offset = usize::try_from(start).map_err(|_| "ZIP 目录位置无效")?;
    let mut names = std::collections::BTreeSet::new();
    let mut count = 0;
    while bytes.get(offset..offset.saturating_add(4)) == Some(b"PK\x01\x02") {
        let header = bytes.get(offset..offset.saturating_add(46)).ok_or("ZIP 目录不完整")?;
        let length = |at| u16::from_le_bytes([header[at], header[at + 1]]) as usize;
        let name_length = length(28);
        let end = offset.checked_add(46).and_then(|n| n.checked_add(name_length)).ok_or("ZIP 目录长度无效")?;
        let name = bytes.get(offset + 46..end).ok_or("ZIP 文件名不完整")?;
        if !names.insert(name) { return Err("ZIP 包含重复路径".into()); }
        offset = end.checked_add(length(30)).and_then(|n| n.checked_add(length(32))).ok_or("ZIP 目录长度无效")?;
        if offset > bytes.len() { return Err("ZIP 目录不完整".into()); }
        count += 1;
        if count > 2000 { return Err("ZIP 条目超过限制".into()); }
    }
    if count != visible_count { return Err("ZIP 目录条目歧义或重复路径".into()); }
    Ok(())
}
fn archive_text(bytes: &[u8]) -> Result<String, String> {
    let mut zip=zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "ZIP 文件结构无效")?;
    validate_raw_directory(bytes, zip.central_directory_start(), zip.len())?;
    if zip.len()>2000 { return Err("ZIP 条目超过限制".into()); }
    let mut paths=std::collections::BTreeSet::new();
    let mut text=String::new();let mut total=0usize;
    for index in 0..zip.len() {
        let entry=zip.by_index(index).map_err(|_| "ZIP 条目不可读")?;
        let path=entry.name().to_owned();
        if entry.enclosed_name().is_none() || path.contains('\\') || path.contains(':') || path.contains('\0') ||
            path.trim_end_matches('/').split('/').any(|p| p.is_empty() || p=="." || p=="..") || !paths.insert(path.clone()) {
            return Err("ZIP 包含不安全或重复路径".into());
        }
        if entry.is_dir() { if entry.size()!=0 {return Err("ZIP 目录条目含异常内容".into());} continue; }
        let mime=match path.rsplit('.').next().unwrap_or_default().to_ascii_lowercase().as_str() {
            "txt"=>"text/plain", "md"|"markdown"=>"text/markdown", "json"=>"application/json", "csv"=>"text/csv",
            "xml"=>"application/xml", "yaml"|"yml"=>"application/x-yaml", "html"|"htm"=>"text/html",
            "docx"=>DOCX, "xlsx"=>XLSX, "pptx"=>PPTX, "pdf"=>"application/pdf",
            _=>return Err("ZIP 含无法完整读取的文件，请先选择或转写其中附件".into()),
        };
        let mut member=Vec::new();entry.take(16*1024*1024+1).read_to_end(&mut member).map_err(|_| "ZIP 正文读取失败")?;
        total+=member.len();if member.len()>16*1024*1024 || total>40*1024*1024 {return Err("ZIP 正文超过提取限制".into());}
        let body=if mime=="application/pdf" {pdf_text(&member)?} else {extract(&member,mime)?};
        text.push_str(&format!("\n\n{}\n{body}",material_header(&path)));
        if text.len()>3_200_000 {return Err("ZIP 可读文本超过完整投影限制".into());}
    }
    if text.trim().is_empty() {return Err("ZIP 没有可读取的正文".into());}
    Ok(text)
}
#[cfg(test)] mod tests {
 fn positioned_pdf_with_content(content:&str)->Vec<u8> {
  let mut document=lopdf::Document::load_mem(include_bytes!("../../tests/fixtures/positioned-material-lines-v1.pdf")).unwrap();
  let pages=document.get_pages();let first=*pages.keys().next().unwrap();
  for (number,id) in pages {
   let bytes=if number==first{content.as_bytes()}else{b"BT ET"};
   let stream=document.add_object(lopdf::Stream::new(lopdf::Dictionary::new(),bytes.to_vec()));
   document.get_object_mut(id).unwrap().as_dict_mut().unwrap().set("Contents",stream);
  }
  let mut result=Vec::new();document.save_to(&mut result).unwrap();result
 }
 fn material_lines(content:&str)->Vec<String>{pdf_text(&positioned_pdf_with_content(content)).unwrap().lines().filter(|line|!line.trim().is_empty()).map(str::to_owned).collect()}
 #[test] fn pdf_horizontal_reposition_and_unshown_moves_do_not_create_fake_lines(){
  for ops in [
   "BT /F1 12 Tf 1 0 0 1 40 740 Tm (same ) Tj 1 0 0 1 75 740 Tm (line) Tj ET",
   "BT /F1 12 Tf 1 0 0 1 40 740 Tm (same ) Tj 1 0 0 1 40 716 Tm 1 0 0 1 75 740 Tm (line) Tj ET",
  ]{assert_eq!(material_lines(ops),vec!["same line"]);}
 }
 #[test] fn pdf_relative_baselines_and_existing_line_operators_keep_one_break_each(){
  assert_eq!(material_lines("BT /F1 12 Tf 40 740 Td (one) Tj 0 -24 Td (two) Tj 0 -24 TD (three) Tj T* (four) Tj ET"),vec!["one","two","three","four"]);
  assert_eq!(material_lines("BT /F1 12 Tf 24 TL 40 740 Td (one) Tj (two) ' 0 0 (three) \" T* (four) Tj ET"),vec!["one","two","three","four"]);
 }
 #[test] fn pdf_rotated_text_uses_perpendicular_baselines_instead_of_raw_y_only(){
  assert_eq!(material_lines("BT /F1 12 Tf 0 1 -1 0 100 100 Tm (same ) Tj 0 1 -1 0 100 135 Tm (line) Tj 0 1 -1 0 124 100 Tm (next) Tj ET"),vec!["same line","next"]);
 }
 #[test] fn pdf_projection_restores_font_geometry_across_graphics_state(){
  assert_eq!(material_lines("BT /F1 12 Tf 1 0 0 1 40 740 Tm (one) Tj q /F1 48 Tf Q 1 0 0 1 40 734 Tm (two) Tj ET"),vec!["one","two"]);
 }

 #[test]
 fn pdf_positioned_material_preserves_each_line_and_fact_without_replacing_the_original() {
  let original=include_bytes!("../../tests/fixtures/positioned-material-lines-v1.pdf");
  let text=pdf_text(original).unwrap();
  let lines=text.lines().filter(|line|line.contains("Public item")).collect::<Vec<_>>();
  assert_eq!(lines.len(),8,"eight separately positioned PDF lines cannot collapse into two page paragraphs");
  for (index,line) in lines.iter().enumerate(){assert!(line.contains(&format!("Public item{}:",index+1)));assert_eq!(line.matches("Public item").count(),1);}
  let plan=crate::attachment_web_search_v1::build(&format!("### 附件：positioned.pdf\n{text}"));
  assert_eq!(plan.items.len(),8,"fact inventory must retain all eight independently checkable statements");
 }

 use super::*;
 use std::io::{Cursor,Write};
 fn zip(parts:&[(&str,&[u8])])->Vec<u8>{let mut w=zip::ZipWriter::new(Cursor::new(Vec::new()));for (name,body) in parts{w.start_file(*name,zip::write::SimpleFileOptions::default()).unwrap();w.write_all(body).unwrap();}w.finish().unwrap().into_inner()}
 #[test] fn all_declared_plain_aliases_preserve_the_twentieth_claim_and_tail(){
  let body=(1..=20).map(|n|format!("事项{n}：2025年发布。\n")).collect::<String>()+"完整末尾";
  for mime in ["text/plain","text/markdown","application/json","text/csv","application/xml","text/xml","application/x-yaml","text/yaml","text/html"]{assert!(supports(mime),"{mime}");assert_eq!(extract(body.as_bytes(),mime).unwrap(),body);}
 }
 #[test] fn archive_keeps_all_members_and_full_material_tail(){let bytes=zip(&[("first.txt",b"first full text"),("folder/second.yaml",b"second full tail")]);let text=extract(&bytes,"application/zip").unwrap();assert!(text.contains("first.txt\nfirst full text"));assert!(text.contains("folder/second.yaml\nsecond full tail"));}
 #[test] fn binary_text_is_rejected_instead_of_sent(){assert!(extract(b"visible\0hidden","text/plain").is_err());assert!(extract(&[0xff],"text/xml").is_err());}
 #[test] fn unsupported_archive_member_is_not_omitted(){let bytes=zip(&[("known.txt",b"known"),("unknown.bin",b"unknown")]);assert!(extract(&bytes,"application/zip").is_err());}

 #[test] fn office_invalid_cdata_is_not_silently_omitted(){
  let sheet=b"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>";
  let x=zip(&[("xl/sharedStrings.xml",b"<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>valid<![CDATA[\xff]]></t></si></sst>"),("xl/worksheets/sheet1.xml",sheet)]);
  assert!(extract(&x,XLSX).is_err());
  let p=zip(&[("ppt/slides/slide1.xml",b"<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>valid<![CDATA[\xff]]></a:t></a:r></a:p></p:sld>")]);
  assert!(extract(&p,PPTX).is_err());
 }
 #[test] fn office_directory_payload_is_rejected(){
  for (mime,name,body) in [(XLSX,"xl/worksheets/sheet1.xml",b"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c><v>1</v></c></row></worksheet>".as_slice()),(PPTX,"ppt/slides/slide1.xml",b"<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>valid</a:t></a:r></a:p></p:sld>".as_slice())]{
   assert!(extract(&zip(&[(name,body)]),mime).is_ok(),"valid control: {mime}");
   let mut bytes=zip(&[(name,body),("folderX",b"directory payload")]);
   let positions:Vec<_>=bytes.windows(7).enumerate().filter_map(|(i,n)|(n==b"folderX").then_some(i)).collect();assert_eq!(positions.len(),2);
   for i in positions{bytes[i+6]=b'/';}
   assert!(extract(&bytes,mime).is_err(),"{mime}");
  }
 }
 #[test] fn archive_rejects_unsafe_paths_and_duplicate_names(){
  for path in ["../escape.txt","C:/escape.txt","folder\\escape.txt","folder/./escape.txt"]{assert!(extract(&zip(&[(path,b"private")]),"application/zip").is_err(),"{path}");}
  let mut bytes=zip(&[("safeA.txt",b"first"),("safeB.txt",b"second")]);
  let positions:Vec<_>=bytes.windows(9).enumerate().filter_map(|(i,n)|(n==b"safeB.txt").then_some(i)).collect();assert_eq!(positions.len(),2);
  for i in positions{bytes[i+4]=b'A';}assert!(extract(&bytes,"application/zip").is_err());
 }

 #[test] fn duplicate_office_parts_are_not_collapsed_into_a_successful_document(){
  for (mime,path,alias,body) in [(DOCX,"word/document.xml","word/documenX.xml",b"<w:p xmlns:w='urn:w'><w:r><w:t>valid</w:t></w:r></w:p>".as_slice()),(XLSX,"xl/worksheets/sheet1.xml","xl/worksheets/sheet2.xml",b"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c><v>42</v></c></row></worksheet>".as_slice()),(PPTX,"ppt/slides/slide1.xml","ppt/slides/slide2.xml",b"<p:sld xmlns:p='urn:p' xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:p><a:r><a:t>valid</a:t></a:r></a:p></p:sld>".as_slice())]{
   if mime==PPTX {assert!(extract(&zip(&[(path,body)]),mime).is_ok(),"valid PPTX control");}
   assert_eq!(path.len(),alias.len());let mut bytes=zip(&[(path,body),(alias,body)]);
   let positions:Vec<_>=bytes.windows(alias.len()).enumerate().filter_map(|(i,n)|(n==alias.as_bytes()).then_some(i)).collect();assert_eq!(positions.len(),2);
   for i in positions{bytes[i..i+alias.len()].copy_from_slice(path.as_bytes());}
   assert!(extract(&bytes,mime).is_err(),"{mime}");
  }
 }
}

#[cfg(test)]
mod material_name_metadata_tests {
    use super::*;
    use std::io::Write;
    fn archive(path:&str,body:&str)->Vec<u8> {
        let mut w=zip::ZipWriter::new(Cursor::new(Vec::new()));w.start_file(path,zip::write::SimpleFileOptions::default()).unwrap();w.write_all(body.as_bytes()).unwrap();w.finish().unwrap().into_inner()
    }
    #[test]
    fn material_header_distinguishes_literal_escape_text_and_controls_without_changing_names() {
        assert_eq!(material_header("a\nb.txt"),"### 附件：a\\nb.txt");
        assert_eq!(material_header("a\\nb.txt"),"### 附件：a\\\\nb.txt");
        assert_eq!(material_header("a\0b.txt"),"### 附件：a\\u0000b.txt");
        assert_eq!(material_header("中文😀 report.txt"),"### 附件：中文😀 report.txt");
    }
    #[test]
    fn archive_member_name_is_one_metadata_line_and_never_a_public_fact() {
        let path="folder/\nZIP_NAME_MARKER_2099 发布.txt";let body="Rust 1.90.0于2025年发布。\nPython 3.14.0于2025年发布。\n";let bytes=archive(path,body);let text=extract(&bytes,"application/zip").unwrap();
        let plan=crate::attachment_web_search_v1::build(&text);assert_eq!(plan.items.len(),2);assert!(!plan.query_text().contains("ZIP_NAME_MARKER_2099"));assert!(text.contains("folder/\\nZIP_NAME_MARKER_2099 发布.txt\n"));assert!(text.contains(body));
    }
    #[test]
    fn archive_control_name_projection_keeps_body_line_breaks_and_safe_names_unchanged() {
        let body="第一段实际正文\n第二段实际正文\n";
        for (ch,escaped) in [('\n',"\\n"),('\r',"\\r"),('\t',"\\t"),('\u{85}',"\\u0085"),('\u{2028}',"\\u2028"),('\u{2029}',"\\u2029"),('\u{1b}',"\\u001B")] {
            let text=extract(&archive(&format!("a{ch}b.txt"),body),"application/zip").unwrap();assert!(text.contains(&format!("### 附件：a{escaped}b.txt\n")),"{escaped}");assert!(text.ends_with(body));
        }
        assert_eq!(extract(&archive("folder/中文😀.txt",body),"application/zip").unwrap(),format!("\n\n### 附件：folder/中文😀.txt\n{body}"));
    }
}

#[cfg(test)] pub(crate) mod v132_visual_pdf_tests {
 use super::*;
 use lopdf::{dictionary,Document,Object,Stream};
 pub(crate) fn pdf(visual:bool)->Vec<u8> {
  let mut doc=Document::with_version("1.5");let pages=doc.new_object_id();
  let font=doc.add_object(dictionary!{"Type"=>"Font","Subtype"=>"Type1","BaseFont"=>"Helvetica"});
  let mut body=b"BT /F1 12 Tf 10 10 Td (Synthetic text 123456) Tj ET".to_vec();
  if visual{body.extend_from_slice(b" 0 0 10 10 re f")}
  let content=doc.add_object(Stream::new(dictionary!{},body));
  let page=doc.add_object(dictionary!{"Type"=>"Page","Parent"=>pages,"Contents"=>content,"Resources"=>dictionary!{"Font"=>dictionary!{"F1"=>font}},"MediaBox"=>vec![0.into(),0.into(),100.into(),100.into()]});
  doc.objects.insert(pages,Object::Dictionary(dictionary!{"Type"=>"Pages","Kids"=>vec![page.into()],"Count"=>1}));
  let catalog=doc.add_object(dictionary!{"Type"=>"Catalog","Pages"=>pages});doc.trailer.set("Root",catalog);
  let mut out=Vec::new();doc.save_to(&mut out).unwrap();out
 }
 #[test] fn v132_pdf_does_not_treat_readable_text_as_complete_visual_projection() {
  assert!(pdf_projection(&pdf(true)).unwrap().is_none());
  assert!(pdf_projection(&pdf(false)).unwrap().unwrap().contains("123456"));
 }
}
