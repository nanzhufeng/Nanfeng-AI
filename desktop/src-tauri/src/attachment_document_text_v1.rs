//! Local full-document projection shared by ordinary chat and its fact-search plan.
use quick_xml::{events::Event, Reader};
use std::io::{Cursor, Read};

pub const DOCX: &str = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
pub const XLSX: &str = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
pub const PPTX: &str = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
pub fn supports(mime: &str) -> bool {
    matches!(mime, "text/plain" | "text/markdown" | "application/json" | "text/csv" |
        "application/xml" | "text/xml" | "application/x-yaml" | "text/yaml" | "text/html" | DOCX | XLSX | PPTX | "application/zip")
}
pub fn pdf_text(bytes: &[u8]) -> Result<String, String> {
    let document=lopdf::Document::load_mem(bytes).map_err(|_| "PDF 文本层无法读取")?;
    if document.is_encrypted() { return Err("加密 PDF 无法本机提取正文".into()); }
    let pages=document.get_pages().keys().copied().collect::<Vec<_>>();
    if pages.len()>2000 { return Err("PDF 页数超出本机提取上限".into()); }
    let mut text=String::new();
    for page in pages {
        text.push_str(&document.extract_text(&[page]).map_err(|_| "PDF 文本层无法完整读取")?);
        text.push('\n');
        if text.len()>3_200_000 { return Err("PDF 可读文本超出完整投影限制".into()); }
    }
    if text.trim().is_empty() { return Err("PDF 没有可读取的文本层，请先使用附件转写".into()); }
    if text.chars().count()>800_000 { return Err("PDF 可读文本超出完整投影限制".into()); }
    Ok(text)
}
pub fn extract(bytes: &[u8], mime: &str) -> Result<String, String> {
    if !supports(mime) { return Err("附件不是受支持的文本格式".into()); }
    let text = match mime {
        "application/zip" => archive_text(bytes)?,
        DOCX => crate::desktop_docx_preview::extract(bytes)?,
        XLSX | PPTX => office(bytes, mime)?,
        _ => {
            if bytes.len() > 2 * 1024 * 1024 { return Err("文本附件超过 2 MB 投影上限".into()); }
            if bytes.contains(&0) {return Err("文本附件含二进制空字节".into());}
            std::str::from_utf8(bytes).map_err(|_| "文本附件不是 UTF-8")?.into()
        }
    };
    if text.chars().count() > 800_000 { return Err("附件可读文本超过完整投影上限，未截断后发送".into()); }
    Ok(text)
}
fn archive_text(bytes: &[u8]) -> Result<String, String> {
    let mut zip=zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "ZIP 文件结构无效")?;
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
        text.push_str(&format!("\n\n### 附件：{path}\n{body}"));
        if text.len()>3_200_000 {return Err("ZIP 可读文本超过完整投影限制".into());}
    }
    if text.trim().is_empty() {return Err("ZIP 没有可读取的正文".into());}
    Ok(text)
}
fn office(bytes: &[u8], mime: &str) -> Result<String, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "Office 文件结构无效")?;
    if zip.len() > 2000 { return Err("Office 条目超过限制".into()); }
    let mut parts = Vec::new();
    let mut total = 0usize;
    for index in 0..zip.len() {
        let entry = zip.by_index(index).map_err(|_| "Office 条目不可读")?;
        if entry.enclosed_name().is_none() || entry.name().contains('\\') { return Err("Office 包含不安全路径".into()); }
        if entry.is_dir() && entry.size()!=0 {return Err("Office 目录条目含异常内容".into());}
        let name = entry.name().to_owned();
        let numbered = |prefix: &str| name.strip_prefix(prefix).and_then(|s| s.strip_suffix(".xml"))
            .is_some_and(|s| !s.is_empty() && s.bytes().all(|b| b.is_ascii_digit()));
        let selected = match mime {
            XLSX => name == "xl/sharedStrings.xml" || numbered("xl/worksheets/sheet"),
            PPTX => numbered("ppt/slides/slide"),
            _ => false,
        };
        if !selected { continue; }
        let mut xml = Vec::new();
        entry.take(4 * 1024 * 1024 + 1).read_to_end(&mut xml).map_err(|_| "Office 正文读取失败")?;
        total += xml.len();
        if xml.len() > 4 * 1024 * 1024 || total > 12 * 1024 * 1024 { return Err("Office 正文超出限制".into()); }
        parts.push((name, xml));
    }
    parts.sort_by_key(|(name, _)| {
        let number = name.trim_end_matches(".xml").rsplit(|c: char| !c.is_ascii_digit()).next()
            .and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
        (name != "xl/sharedStrings.xml", number)
    });
    let mut shared = Vec::new();
    let mut output = String::new();
    for (name, xml) in parts {
        let mut reader = Reader::from_reader(xml.as_slice());
        let mut in_text = false;
        let mut cell_type = String::new();
        let mut value = String::new();
        loop {
            if output.len() > 3_200_000 { return Err("Office 可读文本超出完整投影限制".into()); }
            match reader.read_event().map_err(|_| "Office XML 无效")? {
                Event::DocType(_) => return Err("Office 不允许外部实体".into()),
                Event::Start(e) => match e.local_name().as_ref() {
                    b"si" | b"c" => {
                        value.clear();
                        cell_type.clear();
                        for attribute in e.attributes().with_checks(true) {
                            let attribute=attribute.map_err(|_| "Office XML 属性无效")?;
                            if attribute.key.as_ref()==b"t" {cell_type=attribute.unescape_value().map_err(|_| "Office 单元格类型无效")?.into_owned();}
                        }
                    }
                    b"t" | b"v" => in_text = true,
                    _ => {},
                },
                Event::Text(e) if in_text => {
                    let text = e.unescape().map_err(|_| "Office 文本编码无效")?;
                    if mime == PPTX { output.push_str(&text); } else { value.push_str(&text); }
                }
                Event::CData(e) if in_text => {
                    let text=std::str::from_utf8(e.as_ref()).map_err(|_| "Office 文本编码无效")?;
                    if mime==PPTX {output.push_str(text);} else {value.push_str(text);}
                }
                Event::End(e) => match e.local_name().as_ref() {
                    b"t" | b"v" => in_text = false,
                    b"si" if name == "xl/sharedStrings.xml" => shared.push(value.clone()),
                    b"c" => {
                        if cell_type == "s" {
                            let index = value.parse::<usize>().map_err(|_| "Office 共享文本索引无效")?;
                            output.push_str(shared.get(index).ok_or("Office 共享文本缺失")?);
                        } else { output.push_str(&value); }
                        output.push('\t');
                    }
                    b"row" | b"p" => output.push('\n'),
                    _ => {},
                },
                Event::Empty(e) if e.local_name().as_ref()==b"c" => output.push('\t'),
                Event::Eof => break,
                _ => {},
            }
        }
        output.push('\n');
    }
    if output.trim().is_empty() { return Err("Office 没有可读取的正文".into()); }
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    fn archive(parts: &[(&str, &str)]) -> Vec<u8> {
        let mut writer=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (name, text) in parts { writer.start_file(*name,zip::write::SimpleFileOptions::default()).unwrap();writer.write_all(text.as_bytes()).unwrap(); }
        writer.finish().unwrap().into_inner()
    }
    #[test] fn all_text_formats_reach_fact_search() {
        let fixture: serde_json::Value=serde_json::from_str(include_str!("../../../protocol/fixtures/attachment-document-types-v1.json")).unwrap();
        for mime in fixture["mimeTypes"].as_array().unwrap() { assert!(supports(mime.as_str().unwrap())); }
        for mime in ["text/plain","text/markdown","application/json","text/csv","application/xml","text/xml","application/x-yaml","text/yaml","text/html"] {
            let text=extract(b"(1) Python 3.14.0 released in 2025.",mime).unwrap();
            assert!(!crate::attachment_web_search_v1::build(&text).items.is_empty(),"{mime}");
        }
        for (mime,parts) in [
            (DOCX,vec![("word/document.xml","<w:document xmlns:w='urn:w'><w:p><w:r><w:t><![CDATA[(1) Python 3.14.0 released in 2025.]]></w:t></w:r></w:p></w:document>")]),
            (XLSX,vec![("xl/sharedStrings.xml","<sst><si><t>(1) Python 3.14.0 released in 2025.</t></si></sst>"),("xl/worksheets/sheet1.xml","<worksheet><row><c t='s'><v>0</v></c><c t='inlineStr'><is><t>Rust 1.90.0</t></is></c></row></worksheet>")]),
            (PPTX,vec![("ppt/slides/slide1.xml","<p:sld xmlns:p='urn:p' xmlns:a='urn:a'><a:p><a:r><a:t><![CDATA[(1) Python 3.14.0 released in 2025.]]></a:t></a:r></a:p></p:sld>")]),
        ] {
            let text=extract(&archive(&parts),mime).unwrap();
            assert!(text.contains("Python 3.14.0"),"{mime}");
            assert!(!crate::attachment_web_search_v1::build(&text).items.is_empty());
        }
    }
    #[test] fn every_text_and_office_type_keeps_twenty_claims_and_mixed_tail() {
        let body=(1..=20).map(|i|format!("({i}) Python事项{i}在2025年发布。" )).collect::<Vec<_>>().join("\n");
        let types:serde_json::Value=serde_json::from_str(include_str!("../../../protocol/fixtures/attachment-document-types-v1.json")).unwrap();
        let mut projections=Vec::new();
        for mime in types["mimeTypes"].as_array().unwrap().iter().filter_map(|m|m.as_str()) {
            let bytes=match mime {
                DOCX=>archive(&[("word/document.xml",&format!("<w:document xmlns:w='urn:w'><w:p><w:r><w:t>{body}</w:t></w:r></w:p></w:document>"))]),
                XLSX=>archive(&[("xl/worksheets/sheet1.xml",&format!("<worksheet><row><c t='inlineStr'><is><t>{body}</t></is></c></row></worksheet>"))]),
                PPTX=>archive(&[("ppt/slides/slide1.xml",&format!("<p:sld xmlns:p='urn:p' xmlns:a='urn:a'><a:p><a:r><a:t>{body}</a:t></a:r></a:p></p:sld>"))]),
                _=>body.as_bytes().to_vec(),
            };
            let text=extract(&bytes,mime).unwrap();let plan=crate::attachment_web_search_v1::build(&text);
            assert_eq!(plan.items.len(),20,"{mime}");assert!(!plan.limited);assert!(plan.query_text().contains("Python事项20"));projections.push(text);
        }
        let plan=crate::attachment_web_search_v1::build(&projections.join("\n### 附件\n"));
        assert_eq!(plan.items.len(),20*projections.len());assert!(!plan.limited);
    }
    #[test] fn malformed_and_unsafe_documents_are_not_successful_projections() {
        assert!(extract(&archive(&[("../xl/worksheets/sheet1.xml","<x/>")]),XLSX).is_err());
        assert!(extract(&archive(&[("ppt/slides/slide1.xml","<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///x'>]><x/>")]),PPTX).is_err());
        assert!(extract(&archive(&[("xl/worksheets/sheet1.xml","<worksheet><row><c t='s'><v>99</v></c></row></worksheet>")]),XLSX).is_err());
        assert!(extract(&[0xff],"text/plain").is_err());
        assert!(extract(b"\0binary","text/plain").is_err());
        assert!(extract(b"", "application/msword").is_err());
        assert!(extract("x".repeat(800_001).as_bytes(),"text/plain").is_err());
    }
    #[test] fn pdf_text_layer_is_read_without_using_another_provider() {
        let text=pdf_text(include_bytes!("../tests/fixtures/preview-page.pdf")).unwrap();
        assert!(!text.trim().is_empty());
        assert!(pdf_text(b"%PDF-broken").is_err());
    }
    #[test] fn mixed_archive_contents_keep_each_file_in_search_and_reject_unsupported_members() {
        let bytes=archive(&[("one.md","(1) Python 3.14.0 released in 2025.\n(2) Python 3.13.0 released in 2024."),("two.csv","(1) Rust 1.90.0 released in 2025.")]);
        let text=extract(&bytes,"application/zip").unwrap();let plan=crate::attachment_web_search_v1::build(&text);
        assert!(plan.items[1].query.starts_with("Rust"));assert!(!plan.query_text().contains("one.md"));
        assert!(extract(&archive(&[("one.md","(1) Python 3.14.0"),("two.exe","binary")]),"application/zip").is_err());
        assert!(extract(&archive(&[("../one.md","(1) Python 3.14.0")]),"application/zip").is_err());
        assert!(extract(&archive(&[("fake/","not a directory"),("one.md","(1) Python 3.14.0")]),"application/zip").is_err());
    }
}
