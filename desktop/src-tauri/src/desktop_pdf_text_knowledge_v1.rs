//! P4N text-layer adapter. Never renders, executes actions, opens links, or performs OCR.
//! Production decoding runs only in the resource-limited worker; no business writes here.
use lopdf::{content::Content, Dictionary, Document, LoadOptions, Object, ObjectId};
use crate::desktop_pdf_text_layout_v1::{number,translated,baseline_moved};
use serde::{Deserialize, Serialize};

pub(crate) const MAX_BYTES: usize = 5 * 1024 * 1024;
pub(crate) const MAX_PAGES: usize = 80;
pub(crate) const MAX_PAGE_TEXT: usize = 12_000;
pub(crate) const MAX_TOTAL_TEXT: usize = 120_000;
pub(crate) const EXTRACTION_VERSION: u32 = 4;
pub(crate) const CANDIDATE_VERSION: u32 = 1;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Page {
    pub page_number: u32,
    pub text: String,
    pub text_sha256: String,
    pub code_point_count: usize,
    pub extraction_version: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Item {
    pub ordinal: usize,
    pub page_number: u32,
    pub content: crate::desktop_markdown_knowledge_v1::Candidate,
    pub candidate_sha256: String,
    pub candidate_version: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Parsed {
    pub source_sha256: String,
    pub pages: Vec<Page>,
    pub items: Vec<Item>,
    pub extracted_sha256: String,
    pub extraction_version: u32,
    pub candidate_version: u32,
}

pub(crate) fn preflight(display_name: &str, mime: &str, bytes: &[u8]) -> Result<(), String> {
    let name = regex::Regex::new(r"(?i)^.+\.pdf(?: \(\d+\))?$").expect("fixed PDF name grammar");
    if !mime.eq_ignore_ascii_case("application/pdf") || !name.is_match(display_name)
        || display_name.contains(['/', '\\']) || !bytes.starts_with(b"%PDF-") {
        return Err("NOT_PDF".into());
    }
    if bytes.len() > MAX_BYTES { return Err("TOO_LARGE".into()); }
    let objects = regex::bytes::Regex::new(r"(?m)^[0-9]+\s+[0-9]+\s+obj\b").expect("fixed PDF marker grammar");
    if objects.find_iter(bytes).take(5001).count() > 5000 { return Err("TOO_MANY_OBJECTS".into()); }
    let streams = regex::bytes::Regex::new(r"(?i)(?:\bstream\b|/filter\b)").expect("fixed PDF marker grammar");
    if streams.find_iter(bytes).take(501).count() > 500 { return Err("TOO_MANY_STREAMS".into()); }
    Ok(())
}

// Resource dictionaries are inherited as a whole from the closest Pages ancestor.
fn page_resources(document:&Document,mut page:ObjectId)->Result<Dictionary,String> {
    let mut seen=std::collections::BTreeSet::new();
    for _ in 0..64 {
        if !seen.insert(page){return Err("MALFORMED".into());}
        let node=document.get_dictionary(page).map_err(|_|"MALFORMED")?;
        if let Ok(resource)=node.get(b"Resources") {return resolve_dict(document,resource).cloned();}
        match node.get(b"Parent") {Ok(parent)=>page=parent.as_reference().map_err(|_|"MALFORMED")?,Err(_)=>return Ok(Dictionary::new())}
    }
    Err("TOO_COMPLEX".into())
}
fn resolve_dict<'a>(document:&'a Document,object:&'a Object)->Result<&'a Dictionary,String> {
    match object {Object::Reference(id)=>document.get_dictionary(*id),other=>other.as_dict()}.map_err(|_|"EXTRACTION_NOT_PERMITTED".into())
}
fn resource<'a>(document:&'a Document,resources:&'a Dictionary,kind:&[u8],name:&[u8])->Result<&'a Object,String> {
    resolve_dict(document,resources.get(kind).map_err(|_|"EXTRACTION_NOT_PERMITTED")?)?.get(name).map_err(|_|"EXTRACTION_NOT_PERMITTED".into())
}
struct Traversal {operations:usize,forms:usize,active:std::collections::BTreeSet<ObjectId>}
// lopdf deliberately falls back after malformed Encoding. Knowledge import must not.
fn selected_encoding(document:&Document,font:&Dictionary)->Result<Dictionary,String> {
    let mut descriptor=font.clone();
    if descriptor.has(b"ToUnicode") {descriptor.remove(b"Encoding");return Ok(descriptor);}
    let Ok(mut value)=font.get(b"Encoding") else {return Ok(descriptor);};
    let mut visited=std::collections::BTreeSet::new();
    while let Object::Reference(id)=value {
        if visited.len()>=16 || !visited.insert(*id){return Err("EXTRACTION_NOT_PERMITTED".into());}
        value=document.get_object(*id).map_err(|_|"EXTRACTION_NOT_PERMITTED")?;
    }
    match value {
        Object::Name(_)=>{descriptor.set("Encoding",value.clone());},
        Object::Dictionary(original)=>{
            let mut encoding=original.clone();
            if let Ok(kind)=encoding.get(b"Type") {if kind.as_name().ok()!=Some(b"Encoding".as_slice()){return Err("EXTRACTION_NOT_PERMITTED".into());}}
            encoding.set("Type","Encoding");
            if let Ok(base)=encoding.get(b"BaseEncoding") {
                if !matches!(base.as_name().ok(),Some(b"StandardEncoding"|b"MacRomanEncoding"|b"MacExpertEncoding"|b"WinAnsiEncoding")){return Err("EXTRACTION_NOT_PERMITTED".into());}
            }
            if !encoding.has(b"Differences") {encoding.set("Differences",Vec::<Object>::new());}
            let entries=encoding.get(b"Differences").and_then(Object::as_array).map_err(|_|"EXTRACTION_NOT_PERMITTED")?;
            let mut code=None;
            for entry in entries {
                match entry {
                    Object::Integer(n) if (0..=255).contains(n)=>code=Some(*n),
                    Object::Name(_) if code.is_some_and(|n|n<=255)=>code=code.map(|n|n+1),
                    _=>return Err("EXTRACTION_NOT_PERMITTED".into()),
                }
            }
            descriptor.set("Encoding",encoding);
        },
        _=>return Err("EXTRACTION_NOT_PERMITTED".into()),
    }
    Ok(descriptor)
}
fn decode_selected(encoding:&lopdf::Encoding<'_>,bytes:&[u8])->Result<String,String> {
    let fail=||"EXTRACTION_NOT_PERMITTED".to_owned();
    match encoding {
        lopdf::Encoding::OneByteEncoding(map)=>{
            let mut out=String::new();
            for byte in bytes {
                let glyph=map.get(*byte as usize).copied().flatten().ok_or_else(fail)?;
                out.push_str(&String::from_utf16(&[glyph.utf16_code_unit()]).map_err(|_|fail())?);
            }
            Ok(out)
        },
        lopdf::Encoding::Differences(differences)=>{
            let mut out=String::new();
            for byte in bytes {
                if let Some(glyph)=differences.map.get(byte) {
                    out.push_str(&String::from_utf16(&[glyph.utf16_code_unit()]).map_err(|_|fail())?);
                } else {out.push_str(&decode_selected(&differences.base,&[*byte])?);}
            }
            Ok(out)
        },
        _=>Document::decode_text(encoding,bytes).map_err(|_|fail()),
    }
}
fn append_text(document:&Document,font:&Dictionary,object:&Object,text:&mut String)->Result<(),String> {
    // Clone only the selected descriptor: inline and referenced fonts receive the same
    // Unicode authority, without mutating the source document or borrowing a temporary map.
    let descriptor=selected_encoding(document,font)?;
    let encoding=descriptor.get_font_encoding(document).map_err(|_|"EXTRACTION_NOT_PERMITTED")?;
    let requires_unicode=descriptor.has(b"ToUnicode") || descriptor.get(b"Encoding").and_then(Object::as_name).ok().is_some_and(|n|matches!(n,b"Identity-H"|b"Identity-V"));
    if requires_unicode && !matches!(encoding,lopdf::Encoding::UnicodeMapEncoding(_)){return Err("EXTRACTION_NOT_PERMITTED".into());}
    if descriptor.get(b"Encoding").and_then(Object::as_dict).is_ok() && !matches!(encoding,lopdf::Encoding::Differences(_)){return Err("EXTRACTION_NOT_PERMITTED".into());}
    let bytes=object.as_str().map_err(|_|"MALFORMED")?;
    let decoded=decode_selected(&encoding,bytes)?;
    if decoded.contains('\u{fffd}'){return Err("EXTRACTION_NOT_PERMITTED".into());}
    text.push_str(&decoded);if text.trim().chars().count()>MAX_PAGE_TEXT{return Err("PAGE_TEXT_TOO_LONG".into());}Ok(())
}
fn newline(text:&mut String) {if !text.is_empty() && !text.ends_with('\n'){text.push('\n');}}
#[derive(Clone,Copy)]
struct TextStyle<'a>{font:Option<&'a Dictionary>,size:f64,leading:f64}
impl Default for TextStyle<'_>{fn default()->Self{Self{font:None,size:12.0,leading:0.0}}}
fn extract_content<'a>(document:&'a Document,resources:&'a Dictionary,bytes:&[u8],inherited_style:TextStyle<'a>,depth:usize,budget:&mut Traversal,text:&mut String,cancel:&mut dyn FnMut()->Result<(),String>)->Result<(),String> {
    if depth>16{return Err("TOO_COMPLEX".into());}cancel()?;
    let content=Content::decode(bytes).map_err(|_|"MALFORMED")?;
    let mut style=inherited_style;let mut stack=Vec::new();let mut in_text=false;
    let mut line=[1.0,0.0,0.0,1.0,0.0,0.0];let mut last_shown=None;
    for operation in &content.operations {
        budget.operations=budget.operations.checked_add(1).ok_or("TOO_COMPLEX")?;
        if budget.operations>500_000{return Err("TOO_COMPLEX".into());}
        if budget.operations%256==0{cancel()?;}
        let args=&operation.operands;
        match operation.operator.as_str() {
            "q"=>{if stack.len()>=64{return Err("TOO_COMPLEX".into());}stack.push(style);},
            "Q"=>style=stack.pop().ok_or("MALFORMED")?,
            "BT"=>{if in_text{return Err("MALFORMED".into());}in_text=true;line=[1.0,0.0,0.0,1.0,0.0,0.0];last_shown=None;},
            "ET"=>{if !in_text{return Err("MALFORMED".into());}in_text=false;newline(text);},
            "Tf"=>{if args.len()!=2{return Err("MALFORMED".into());}let name=args[0].as_name().map_err(|_|"MALFORMED")?;style.font=Some(resolve_dict(document,resource(document,resources,b"Font",name)?)?);style.size=number(&args[1])?;},
            "gs"=>{
                if args.len()!=1{return Err("MALFORMED".into());}let name=args[0].as_name().map_err(|_|"MALFORMED")?;
                let graphics=resolve_dict(document,resource(document,resources,b"ExtGState",name)?)?;
                if let Ok(selected)=graphics.get(b"Font") {let values=selected.as_array().map_err(|_|"MALFORMED")?;if values.len()!=2{return Err("MALFORMED".into());}style.font=Some(resolve_dict(document,&values[0])?);style.size=number(&values[1])?;}
            },
            "TL"=>{if args.len()!=1{return Err("MALFORMED".into());}style.leading=number(&args[0])?;},
            "Td"|"TD"|"Tm"=>{
                if !in_text{return Err("MALFORMED".into());}
                let expected=if operation.operator=="Tm"{6}else{2};if args.len()!=expected{return Err("MALFORMED".into());}
                let values=args.iter().map(number).collect::<Result<Vec<_>,_>>()?;
                let next=if expected==6{[values[0],values[1],values[2],values[3],values[4],values[5]]}else{if operation.operator=="TD"{style.leading=-values[1];}translated(line,values[0],values[1])};
                if last_shown.is_some_and(|previous|baseline_moved(previous,next,style.size)){newline(text);}
                line=next;
            },
            "Tj"|"'"|"\""=>{
                if !in_text{return Err("MALFORMED".into());}
                let index=if operation.operator=="\""{2}else{0};
                if args.len()!=index+1{return Err("MALFORMED".into());}
                if operation.operator!="Tj"{line=translated(line,0.0,-style.leading);newline(text);if index==2{number(&args[0])?;number(&args[1])?;}}
                append_text(document,style.font.ok_or("EXTRACTION_NOT_PERMITTED")?,&args[index],text)?;last_shown=Some(line);
            },
            "TJ"=>{
                if !in_text || args.len()!=1{return Err("MALFORMED".into());}
                let selected=style.font.ok_or("EXTRACTION_NOT_PERMITTED")?;
                for item in args[0].as_array().map_err(|_|"MALFORMED")? {
                    match item {Object::String(_,_)=>append_text(document,selected,item,text)?,Object::Integer(n)=>{if *n< -100 && !text.ends_with(' '){text.push(' ');}},Object::Real(n)=>{if *n< -100.0 && !text.ends_with(' '){text.push(' ');}},_=>return Err("MALFORMED".into())}
                }
                last_shown=Some(line);
            },
            "T*"=>{if !in_text{return Err("MALFORMED".into());}line=translated(line,0.0,-style.leading);newline(text);},
            "Do"=>{
                if args.len()!=1 || in_text{return Err("MALFORMED".into());}
                let name=args[0].as_name().map_err(|_|"MALFORMED")?;
                let object=resource(document,resources,b"XObject",name)?;
                let id=object.as_reference().ok();let object=if let Some(id)=id{document.get_object(id).map_err(|_|"MALFORMED")?}else{object};
                let stream=object.as_stream().map_err(|_|"MALFORMED")?;
                match stream.dict.get(b"Subtype").and_then(Object::as_name).map_err(|_|"MALFORMED")? {
                    b"Image"=>{},
                    b"Form"=>{
                        budget.forms+=1;if budget.forms>2048{return Err("TOO_COMPLEX".into());}
                        if let Some(id)=id{if !budget.active.insert(id){return Err("MALFORMED".into());}}
                        let pdf2=document.version.starts_with("2.") || document.catalog().ok().and_then(|c|c.get(b"Version").ok()).and_then(|v|v.as_name().ok()).is_some_and(|v|v.starts_with(b"2."));
                        let nested=match stream.dict.get(b"Resources"){Ok(r)=>resolve_dict(document,r)?,Err(_) if pdf2=>return Err("EXTRACTION_NOT_PERMITTED".into()),Err(_)=>resources};
                        let content=if stream.dict.has(b"Filter"){stream.decompressed_content().map_err(|_|"MALFORMED")?}else{stream.content.clone()};
                        extract_content(document,nested,&content,style,depth+1,budget,text,cancel)?;
                        if let Some(id)=id{budget.active.remove(&id);}
                    },
                    _=>return Err("UNSUPPORTED_TEXT_STRUCTURE".into()),
                }
            },
            _=>{},
        }
        if text.trim().chars().count()>MAX_PAGE_TEXT{return Err("PAGE_TEXT_TOO_LONG".into());}
    }
    if in_text || !stack.is_empty(){return Err("MALFORMED".into());}Ok(())
}

/// Callback is cancellation/progress only; no formal Knowledge mutation occurs in this adapter.
/// Failure returns no partial artifact bundle, even when earlier page progress was observed.
pub(crate) fn parse(
    display_name: &str, mime: &str, bytes: &[u8],
    mut progress: impl FnMut(usize, usize) -> Result<(), String>,
) -> Result<Parsed, String> {
    preflight(display_name, mime, bytes)?;
    progress(0, 0)?;
    let document = Document::load_mem_with_options(bytes, LoadOptions { strict: true, ..Default::default() })
        .map_err(|error| match error {
            lopdf::Error::Decryption(_) | lopdf::Error::AlreadyEncrypted => "ENCRYPTED",
            _ => "MALFORMED",
        })?;
    if document.is_encrypted() || document.encryption_state.is_some() { return Err("ENCRYPTED".into()); }
    let page_ids = document.get_pages();
    if page_ids.is_empty() { return Err("EMPTY_DOCUMENT".into()); }
    if page_ids.len() > MAX_PAGES { return Err("TOO_MANY_PAGES".into()); }
    let mut pages = Vec::new();
    let mut items = Vec::new();
    let mut total = 0usize;
    let mut traversal=Traversal{operations:0,forms:0,active:std::collections::BTreeSet::new()};
    for (&page_number, &page_id) in &page_ids {
        progress(pages.len(), page_ids.len())?;
        let resources=page_resources(&document,page_id)?;
        let content=document.get_page_content(page_id).map_err(|_|"MALFORMED")?;
        let mut text=String::new();
        extract_content(&document,&resources,&content,TextStyle::default(),0,&mut traversal,&mut text,&mut ||progress(pages.len(),page_ids.len()))?;
        let text=text.replace("\r\n","\n").replace('\r',"\n").trim().to_owned();
        if text.contains('\u{fffd}') { return Err("EXTRACTION_NOT_PERMITTED".into()); }
        let code_point_count = text.chars().count();
        if code_point_count > MAX_PAGE_TEXT { return Err("PAGE_TEXT_TOO_LONG".into()); }
        total = total.checked_add(code_point_count).ok_or("TOTAL_TEXT_TOO_LONG")?;
        if total > MAX_TOTAL_TEXT { return Err("TOTAL_TEXT_TOO_LONG".into()); }
        crate::require_safe_memory_body(&text).map_err(|_| "HIGH_SENSITIVITY")?;
        if !text.is_empty() {
            let title: String = text.lines().find(|line| !line.trim().is_empty())
                .unwrap_or("PDF 文本资料").trim().chars().take(120).collect();
            let candidate = crate::desktop_markdown_knowledge_v1::validate_candidate(
                crate::desktop_markdown_knowledge_v1::Candidate { title, body: text.clone(), tags: Vec::new() })?;
            items.push(Item { ordinal: items.len(), page_number,
                candidate_sha256: crate::sha256(format!("{page_number}\\n{text}").as_bytes()),
                content: candidate, candidate_version: CANDIDATE_VERSION });
        }
        pages.push(Page { page_number, text_sha256: crate::sha256(text.as_bytes()), text,
            code_point_count, extraction_version: EXTRACTION_VERSION });
        progress(pages.len(), page_ids.len())?;
    }
    if items.is_empty() { return Err("NO_TEXT_LAYER".into()); }
    let joined = pages.iter().map(|p| format!("{}:{}", p.page_number, p.text_sha256)).collect::<Vec<_>>().join("|");
    Ok(Parsed { source_sha256: crate::sha256(bytes), extracted_sha256: crate::sha256(joined.as_bytes()),
        pages, items, extraction_version: EXTRACTION_VERSION, candidate_version: CANDIDATE_VERSION })
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use lopdf::{dictionary, content::Operation, Stream};

    pub(crate) fn pdf(texts: &[&str], inert_actions: bool) -> Vec<u8> {
        let mut d = Document::with_version("1.5");
        let pages = d.new_object_id();
        let font = d.add_object(dictionary! {"Type"=>"Font", "Subtype"=>"Type1", "BaseFont"=>"Helvetica", "Encoding"=>"WinAnsiEncoding"});
        let mut kids = Vec::new();
        for text in texts {
            let mut operations = Vec::new();
            if !text.is_empty() {
                operations.push(Operation::new("BT", vec![]));
                operations.push(Operation::new("Tf", vec![Object::Name(b"F1".to_vec()), Object::Integer(12)]));
                for (index,line) in text.lines().enumerate() {
                    if index>0 { operations.push(Operation::new("T*",vec![])); }
                    operations.push(Operation::new("Tj",vec![Object::string_literal(line)]));
                }
                operations.push(Operation::new("ET",vec![]));
            }
            let content = d.add_object(Stream::new(dictionary! {}, Content { operations }.encode().unwrap()));
            let page = d.add_object(dictionary! {"Type"=>"Page", "Parent"=>pages, "Contents"=>content,
                "MediaBox"=>vec![0.into(),0.into(),595.into(),842.into()], "Resources"=>dictionary!{"Font"=>dictionary!{"F1"=>font}}});
            kids.push(page.into());
        }
        d.objects.insert(pages, dictionary! {"Type"=>"Pages", "Kids"=>kids, "Count"=>texts.len() as i64}.into());
        let mut catalog = dictionary! {"Type"=>"Catalog", "Pages"=>pages};
        if inert_actions {
            catalog.set("OpenAction", dictionary! {"S"=>"JavaScript", "JS"=>Object::string_literal("throw unsafe")});
            catalog.set("URI", Object::string_literal("https://example.invalid/never-open"));
            d.add_object(Stream::new(dictionary!{"Type"=>"EmbeddedFile"}, b"inert attachment".to_vec()));
        }
        let catalog = d.add_object(catalog); d.trailer.set("Root", catalog);
        let mut bytes=Vec::new();d.save_to(&mut bytes).unwrap();bytes
    }

    fn run(bytes: &[u8]) -> Result<Parsed,String> { parse("synthetic.pdf","application/pdf",bytes,|_,_|Ok(())) }

    #[test]
    fn pdf_text_adapter_page_facts_and_inert_actions_are_deterministic() {
        let bytes=pdf(&["First page", "", "Second page\r\nBody"],true);
        let mut progress=Vec::new();let x=parse("synthetic.pdf","application/pdf",&bytes,|done,total|{progress.push((done,total));Ok(())}).unwrap();
        assert_eq!(x.pages.len(),3);assert_eq!(x.items.len(),2);assert_eq!(x.items[1].page_number,3);
        assert_eq!(x.items[1].content.body,"Second page\nBody");assert_eq!(x.items[1].content.title,"Second page");
        assert_eq!(progress.last(),Some(&(3,3)));assert_eq!(x.source_sha256,crate::sha256(&bytes));
        assert!(x.pages.iter().all(|p|p.text_sha256==crate::sha256(p.text.as_bytes()) && p.code_point_count==p.text.chars().count()));
        assert_eq!(x,run(&bytes).unwrap());assert!(!serde_json::to_string(&x).unwrap().contains("example.invalid"));
    }

    #[test]
    fn pdf_text_adapter_strict_mime_header_size_and_marker_bounds() {
        let bytes=pdf(&["Safe body"],false);
        for (name,mime,data,error) in [("synthetic.txt","application/pdf",bytes.as_slice(),"NOT_PDF"),("synthetic.pdf","text/plain",bytes.as_slice(),"NOT_PDF"),("../synthetic.pdf","application/pdf",bytes.as_slice(),"NOT_PDF"),("synthetic.pdf","application/pdf",b"not pdf".as_slice(),"NOT_PDF")] {
            assert_eq!(parse(name,mime,data,|_,_|Ok(())).unwrap_err(),error);
        }
        let mut large=vec![b' ';MAX_BYTES+1];large[..5].copy_from_slice(b"%PDF-");assert_eq!(run(&large).unwrap_err(),"TOO_LARGE");
        let objects=format!("%PDF-1.5\n{}","1 0 obj\n".repeat(5001));assert_eq!(run(objects.as_bytes()).unwrap_err(),"TOO_MANY_OBJECTS");
        let streams=format!("%PDF-1.5\n{}","stream\n".repeat(501));assert_eq!(run(streams.as_bytes()).unwrap_err(),"TOO_MANY_STREAMS");
        let filters=format!("%PDF-1.5\n{}","/Filter /FlateDecode\n".repeat(501));assert_eq!(run(filters.as_bytes()).unwrap_err(),"TOO_MANY_STREAMS");
        assert_eq!(run(b"%PDF-1.5\ninvalid").unwrap_err(),"MALFORMED");
    }

    #[test]
    fn pdf_text_adapter_empty_and_no_text_are_failures_not_candidates() {
        assert_eq!(run(&pdf(&[],false)).unwrap_err(),"EMPTY_DOCUMENT");
        assert_eq!(run(&pdf(&["",""],false)).unwrap_err(),"NO_TEXT_LAYER");
    }

    #[test]
    fn pdf_text_adapter_page_and_total_limits_refuse_entire_bundle() {
        assert_eq!(run(&pdf(&vec!["small";81],false)).unwrap_err(),"TOO_MANY_PAGES");
        let long="x".repeat(MAX_PAGE_TEXT+1);assert_eq!(run(&pdf(&[&long],false)).unwrap_err(),"PAGE_TEXT_TOO_LONG");
        let bounded="x".repeat(MAX_PAGE_TEXT);assert_eq!(run(&pdf(&vec![bounded.as_str();11],false)).unwrap_err(),"TOTAL_TEXT_TOO_LONG");
        assert_eq!(run(&pdf(&[&bounded],false)).unwrap().pages[0].code_point_count,MAX_PAGE_TEXT);
    }

    #[test]
    fn pdf_text_adapter_cancellation_after_page_discards_all_candidates() {
        let bytes=pdf(&["First","Second"],false);
        assert_eq!(parse("synthetic.pdf","application/pdf",&bytes,|done,_|if done==1{Err("INTERRUPTED".into())}else{Ok(())}).unwrap_err(),"INTERRUPTED");
    }

    #[test]
    fn pdf_text_adapter_sensitive_text_is_rejected_before_any_bundle() {
        assert_eq!(run(&pdf(&["ordinary page", "password: synthetic-only"],false)).unwrap_err(),"HIGH_SENSITIVITY");
    }

    #[test]
    fn pdf_text_adapter_unicode_cmap_and_compressed_page_are_preserved() {
        let mut document=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();
        let cmap=document.add_object(Stream::new(dictionary!{},b"/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Synthetic def\n/CMapType 2 def\n1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n2 beginbfchar\n<0001> <4F60>\n<0002> <597D>\nendbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend".to_vec()));
        for object in document.objects.values_mut() {
            if let Ok(dict)=object.as_dict_mut() {
                if dict.get(b"Type").and_then(Object::as_name).ok()==Some(b"Font".as_slice()) {dict.set("ToUnicode",cmap);}
            }
            if let Ok(stream)=object.as_stream_mut() {
                if stream.content.starts_with(b"BT") {
                    stream.set_content(Content {operations:vec![Operation::new("BT",vec![]),Operation::new("Tf",vec![Object::Name(b"F1".to_vec()),12.into()]),Operation::new("Tj",vec![Object::String(vec![0,1,0,2],lopdf::StringFormat::Hexadecimal)]),Operation::new("ET",vec![])]}.encode().unwrap());
                    stream.compress().unwrap();
                }
            }
        }
        let mut bytes=Vec::new();document.save_to(&mut bytes).unwrap();let x=run(&bytes).unwrap();
        assert_eq!(x.items[0].content.body,"你好");assert_eq!(x.pages[0].code_point_count,2);
    }

    #[test]
    fn pdf_text_adapter_bad_unicode_map_refuses_standard_encoding_fallback() {
        let mut document=Document::load_mem(&pdf(&["text"],false)).unwrap();
        let cmap=document.add_object(Stream::new(dictionary!{},b"not a valid Unicode map".to_vec()));
        for object in document.objects.values_mut() {
            if let Ok(dict)=object.as_dict_mut() {
                if dict.get(b"Type").and_then(Object::as_name).ok()==Some(b"Font".as_slice()) {dict.set("ToUnicode",cmap);}
            }
        }
        let mut bytes=Vec::new();document.save_to(&mut bytes).unwrap();assert_eq!(run(&bytes).unwrap_err(),"EXTRACTION_NOT_PERMITTED");
    }

    fn encoding_pdf(encoding:Object,bytes:Vec<u8>)->Vec<u8> {
        let mut d=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();
        for object in d.objects.values_mut() {
            if let Ok(font)=object.as_dict_mut() {
                if font.get(b"Type").and_then(Object::as_name).ok()==Some(b"Font".as_slice()) {font.set("Encoding",encoding.clone());}
            }
        }
        set_page_content(&mut d,line(Object::String(bytes,lopdf::StringFormat::Hexadecimal)));save(&mut d)
    }
    #[test]
    fn pdf_text_adapter_encoding_dictionary_without_optional_type_preserves_differences() {
        let encoding=Object::Dictionary(dictionary!{"BaseEncoding"=>"WinAnsiEncoding","Differences"=>vec![65.into(),Object::Name(b"Euro".to_vec()),Object::Name(b"Aacute".to_vec())]});
        let mut d=Document::load_mem(&encoding_pdf(encoding,b"ABC".to_vec())).unwrap();
        let id=d.new_object_id();
        // The selected font Encoding may be indirect; do not confuse it with a fallback map.
        let mut value=None;
        for object in d.objects.values() {if let Ok(font)=object.as_dict(){if font.get(b"Type").and_then(Object::as_name).ok()==Some(b"Font".as_slice()){value=Some(font.get(b"Encoding").unwrap().clone());break;}}}
        d.objects.insert(id,value.unwrap());
        for object in d.objects.values_mut(){if let Ok(font)=object.as_dict_mut(){if font.get(b"Type").and_then(Object::as_name).ok()==Some(b"Font".as_slice()){font.set("Encoding",id);}}}
        let parsed=run(&save(&mut d)).unwrap();assert_eq!(parsed.pages[0].text,"€ÁC");
    }
    #[test]
    fn pdf_text_adapter_invalid_differences_never_publish_standard_fallback_text() {
        for differences in [vec![300.into(),Object::Name(b"Aacute".to_vec())],vec![65.into(),Object::Name(b"not_a_real_glyph".to_vec())],vec![Object::String(b"bad".to_vec(),lopdf::StringFormat::Literal)]] {
            let encoding=Object::Dictionary(dictionary!{"Type"=>"Encoding","BaseEncoding"=>"WinAnsiEncoding","Differences"=>differences});
            assert_eq!(run(&encoding_pdf(encoding,b"ABC".to_vec())).unwrap_err(),"EXTRACTION_NOT_PERMITTED");
        }
    }
    #[test]
    fn pdf_text_adapter_unmapped_single_byte_glyph_is_not_silently_removed() {
        assert_eq!(run(&encoding_pdf(Object::Name(b"StandardEncoding".to_vec()),vec![65,0,66])).unwrap_err(),"EXTRACTION_NOT_PERMITTED");
        assert_eq!(run(&encoding_pdf(Object::Name(b"WinAnsiEncoding".to_vec()),vec![65,0x80,66])).unwrap().pages[0].text,"A€B");
    }

    #[test]
    fn pdf_text_adapter_base_only_encoding_and_invalid_owner_descriptors_are_distinct() {
        let valid=Object::Dictionary(dictionary!{"BaseEncoding"=>"WinAnsiEncoding"});
        assert_eq!(run(&encoding_pdf(valid,vec![65,0x80,66])).unwrap().pages[0].text,"A€B");
        for invalid in [Object::Integer(7),Object::Dictionary(dictionary!{"Type"=>"Other","Differences"=>Vec::<Object>::new()}),Object::Dictionary(dictionary!{"BaseEncoding"=>"Unknown","Differences"=>vec![65.into(),Object::Name(b"Aacute".to_vec())]}),Object::Dictionary(dictionary!{"Differences"=>vec![255.into(),Object::Name(b"Aacute".to_vec()),Object::Name(b"Euro".to_vec())]})] {
            assert_eq!(run(&encoding_pdf(invalid,b"ABC".to_vec())).unwrap_err(),"EXTRACTION_NOT_PERMITTED");
        }
        let mut d=Document::load_mem(&encoding_pdf(Object::Reference((90,0)),b"ABC".to_vec())).unwrap();
        d.objects.insert((90,0),Object::Reference((91,0)));d.objects.insert((91,0),Object::Reference((90,0)));
        assert_eq!(run(&save(&mut d)).unwrap_err(),"EXTRACTION_NOT_PERMITTED");
    }

    fn save(document:&mut Document)->Vec<u8> {let mut bytes=Vec::new();document.save_to(&mut bytes).unwrap();bytes}
    fn content(operations:Vec<Operation>)->Vec<u8> {Content{operations}.encode().unwrap()}
    fn line(value:Object)->Vec<Operation> {vec![Operation::new("BT",vec![]),Operation::new("Tf",vec![Object::Name(b"F1".to_vec()),12.into()]),Operation::new("Tj",vec![value]),Operation::new("ET",vec![])]}
    fn set_page_content(d:&mut Document,operations:Vec<Operation>) {
        let page=*d.get_pages().values().next().unwrap();let id=d.add_object(Stream::new(dictionary!{},content(operations)));d.get_dictionary_mut(page).unwrap().set("Contents",id);
    }
    #[test]
    fn pdf_text_adapter_nested_compressed_forms_use_local_inline_unicode_fonts_restore_and_repeat_in_paint_order() {
        let mut d=Document::load_mem(&pdf(&["placeholder"],true)).unwrap();
        let cmap=d.add_object(Stream::new(dictionary!{},b"/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Synthetic def\n/CMapType 2 def\n1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n2 beginbfchar\n<0001> <4F60>\n<0002> <597D>\nendbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend".to_vec()));
        let ascii=dictionary!{"Type"=>"Font","Subtype"=>"Type1","BaseFont"=>"Helvetica"};
        let inner=d.add_object(Stream::new(dictionary!{"Type"=>"XObject","Subtype"=>"Form","BBox"=>vec![0.into(),0.into(),300.into(),300.into()],"Resources"=>dictionary!{"Font"=>dictionary!{"F1"=>ascii}}},content(line(Object::string_literal("Inner")))));
        let unicode=dictionary!{"Type"=>"Font","Subtype"=>"Type1","BaseFont"=>"Helvetica","Encoding"=>"WinAnsiEncoding","ToUnicode"=>cmap};
        let mut ops=line(Object::String(vec![0,1],lopdf::StringFormat::Hexadecimal));ops.push(Operation::new("Do",vec![Object::Name(b"Inner".to_vec())]));ops.extend(line(Object::String(vec![0,2],lopdf::StringFormat::Hexadecimal)));
        let mut form=Stream::new(dictionary!{"Type"=>"XObject","Subtype"=>"Form","BBox"=>vec![0.into(),0.into(),300.into(),300.into()],"Resources"=>dictionary!{"Font"=>dictionary!{"F1"=>unicode},"XObject"=>dictionary!{"Inner"=>inner}}},content(ops));form.compress().unwrap();let outer=d.add_object(form);
        let page=*d.get_pages().values().next().unwrap();d.get_dictionary_mut(page).unwrap().get_mut(b"Resources").unwrap().as_dict_mut().unwrap().set("XObject",dictionary!{"Outer"=>outer});
        let mut ops=line(Object::string_literal("Before"));ops.push(Operation::new("q",vec![]));ops.push(Operation::new("Do",vec![Object::Name(b"Outer".to_vec())]));ops.push(Operation::new("Q",vec![]));
        ops.extend([Operation::new("BT",vec![]),Operation::new("Tj",vec![Object::string_literal("After")]),Operation::new("ET",vec![]),Operation::new("Do",vec![Object::Name(b"Outer".to_vec())])]);
        set_page_content(&mut d,ops);let x=run(&save(&mut d)).unwrap();assert_eq!(x.pages[0].text,"Before\n你\nInner\n好\nAfter\n你\nInner\n好");assert_eq!(x.extraction_version,EXTRACTION_VERSION);assert_eq!(x.items.len(),1);
    }
    #[test]
    fn pdf_text_adapter_inherited_page_resources_and_form_without_local_resources_are_resolved() {
        let mut d=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();let page=*d.get_pages().values().next().unwrap();let parent=d.get_dictionary(page).unwrap().get(b"Parent").unwrap().as_reference().unwrap();
        let form=d.add_object(Stream::new(dictionary!{"Type"=>"XObject","Subtype"=>"Form","BBox"=>vec![0.into(),0.into(),300.into(),300.into()]},content(line(Object::string_literal("Inherited text")))));
        let mut resources=d.get_dictionary(page).unwrap().get(b"Resources").unwrap().as_dict().unwrap().clone();resources.set("XObject",dictionary!{"Form"=>form});let resource_id=d.add_object(resources);d.get_dictionary_mut(parent).unwrap().set("Resources",resource_id);d.get_dictionary_mut(page).unwrap().remove(b"Resources");
        set_page_content(&mut d,vec![Operation::new("Do",vec![Object::Name(b"Form".to_vec())])]);assert_eq!(run(&save(&mut d)).unwrap().pages[0].text,"Inherited text");d.version="2.0".into();assert_eq!(run(&save(&mut d)).unwrap_err(),"EXTRACTION_NOT_PERMITTED");
    }
    #[test]
    fn pdf_text_adapter_form_cycles_depth_and_repeated_call_budget_refuse_entire_bundle() {
        for mode in ["cycle","depth","calls"] {
            let mut d=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();let page=*d.get_pages().values().next().unwrap();
            let mut head=d.add_object(Stream::new(dictionary!{"Type"=>"XObject","Subtype"=>"Form","BBox"=>vec![0.into(),0.into(),300.into(),300.into()]},content(line(Object::string_literal("a")))));
            if mode=="cycle" {
                let stream=d.get_object_mut(head).unwrap().as_stream_mut().unwrap();stream.dict.set("Resources",dictionary!{"XObject"=>dictionary!{"Next"=>head}});stream.set_content(content(vec![Operation::new("Do",vec![Object::Name(b"Next".to_vec())])]));
            } else if mode=="depth" {
                for _ in 0..17 {head=d.add_object(Stream::new(dictionary!{"Type"=>"XObject","Subtype"=>"Form","BBox"=>vec![0.into(),0.into(),300.into(),300.into()],"Resources"=>dictionary!{"XObject"=>dictionary!{"Next"=>head}}},content(vec![Operation::new("Do",vec![Object::Name(b"Next".to_vec())])])));}
            }
            d.get_dictionary_mut(page).unwrap().get_mut(b"Resources").unwrap().as_dict_mut().unwrap().set("XObject",dictionary!{"Next"=>head});
            set_page_content(&mut d,(0..if mode=="calls"{2049}else{1}).map(|_|Operation::new("Do",vec![Object::Name(b"Next".to_vec())])).collect());
            assert_eq!(run(&save(&mut d)).unwrap_err(),if mode=="cycle"{"MALFORMED"}else{"TOO_COMPLEX"});
        }
    }
    #[test]
    fn pdf_text_adapter_text_operator_state_quotes_and_numeric_spacing_preserve_text() {
        let mut d=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();
        set_page_content(&mut d,vec![Operation::new("BT",vec![]),Operation::new("Tf",vec![Object::Name(b"F1".to_vec()),12.into()]),Operation::new("TJ",vec![Object::Array(vec![Object::string_literal("Hello"),Object::Real(-120.0),Object::string_literal("World")])]),Operation::new("'",vec![Object::string_literal("next")]),Operation::new("\"",vec![0.into(),0.into(),Object::string_literal("last")]),Operation::new("ET",vec![])]);
        assert_eq!(run(&save(&mut d)).unwrap().pages[0].text,"Hello World\nnext\nlast");
        let mut budget=Traversal{operations:500_000,forms:0,active:Default::default()};let mut text=String::new();
        assert_eq!(extract_content(&d,&Dictionary::new(),&content(vec![Operation::new("q",vec![])]),TextStyle::default(),0,&mut budget,&mut text,&mut ||Ok(())).unwrap_err(),"TOO_COMPLEX");
    }
    #[test]
    fn pdf_text_adapter_absolute_relative_rotated_baselines_and_leading_preserve_paragraphs() {
        let mut d=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();
        let matrix=|values:&[i64]|Operation::new("Tm",values.iter().copied().map(Object::Integer).collect());
        set_page_content(&mut d,vec![Operation::new("BT",vec![]),Operation::new("Tf",vec![Object::Name(b"F1".to_vec()),12.into()]),matrix(&[1,0,0,1,20,260]),Operation::new("Tj",vec![Object::string_literal("Title")]),Operation::new("Td",vec![0.into(),(-14).into()]),Operation::new("Tj",vec![Object::string_literal("Body")]),Operation::new("TD",vec![0.into(),(-14).into()]),Operation::new("Tj",vec![Object::string_literal("Next")]),Operation::new("T*",vec![]),Operation::new("Tj",vec![Object::string_literal("Last")]),matrix(&[1,0,0,1,20,200]),Operation::new("Tj",vec![Object::string_literal("Matrix")]),matrix(&[1,0,0,1,60,200]),Operation::new("Tj",vec![Object::string_literal(" same row")]),matrix(&[0,1,-1,0,100,200]),Operation::new("Tj",vec![Object::string_literal("Rotated")]),Operation::new("Td",vec![0.into(),(-14).into()]),Operation::new("Tj",vec![Object::string_literal("Rotated next")]),Operation::new("ET",vec![])]);
        assert_eq!(run(&save(&mut d)).unwrap().pages[0].text,"Title\nBody\nNext\nLast\nMatrix same row\nRotated\nRotated next");
    }
    #[test]
    fn pdf_text_adapter_graphics_state_font_selection_and_restore_use_the_actual_font() {
        let mut d=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();
        let cmap=d.add_object(Stream::new(dictionary!{},b"/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Synthetic def\n/CMapType 2 def\n1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n1 beginbfchar\n<0001> <4F60>\nendbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend".to_vec()));
        let font=d.add_object(dictionary!{"Type"=>"Font","Subtype"=>"Type1","BaseFont"=>"Helvetica","Encoding"=>"WinAnsiEncoding","ToUnicode"=>cmap});
        let state=d.add_object(dictionary!{"Type"=>"ExtGState","Font"=>vec![font.into(),12.into()]});let page=*d.get_pages().values().next().unwrap();d.get_dictionary_mut(page).unwrap().get_mut(b"Resources").unwrap().as_dict_mut().unwrap().set("ExtGState",dictionary!{"Unicode"=>state});
        let mut ops=line(Object::string_literal("Before"));ops.extend([Operation::new("q",vec![]),Operation::new("gs",vec![Object::Name(b"Unicode".to_vec())]),Operation::new("BT",vec![]),Operation::new("Tj",vec![Object::String(vec![0,1],lopdf::StringFormat::Hexadecimal)]),Operation::new("ET",vec![]),Operation::new("Q",vec![]),Operation::new("BT",vec![]),Operation::new("Tj",vec![Object::string_literal("After")]),Operation::new("ET",vec![])]);set_page_content(&mut d,ops);
        assert_eq!(run(&save(&mut d)).unwrap().pages[0].text,"Before\n你\nAfter");
    }
    #[test]
    fn pdf_text_adapter_invalid_text_positions_and_missing_graphics_state_refuse_all_candidates() {
        for operation in [Operation::new("Td",vec![Object::Name(b"not-number".to_vec()),1.into()]),Operation::new("Tm",vec![1.into()]),Operation::new("gs",vec![Object::Name(b"Missing".to_vec())])] {
            let mut d=Document::load_mem(&pdf(&["first"],false)).unwrap();let mut ops=line(Object::string_literal("valid first text"));ops.push(Operation::new("BT",vec![]));ops.push(operation.clone());ops.push(Operation::new("ET",vec![]));set_page_content(&mut d,ops);
            let error=run(&save(&mut d)).unwrap_err();assert_eq!(error,if operation.operator=="gs"{"EXTRACTION_NOT_PERMITTED"}else{"MALFORMED"});
        }
    }
    #[test]
    fn pdf_text_adapter_form_cancellation_and_sensitive_text_have_no_partial_bundle() {
        let mut d=Document::load_mem(&pdf(&["placeholder"],false)).unwrap();let page=*d.get_pages().values().next().unwrap();
        let form=d.add_object(Stream::new(dictionary!{"Type"=>"XObject","Subtype"=>"Form","BBox"=>vec![0.into(),0.into(),300.into(),300.into()]},content(line(Object::string_literal("password: synthetic-only")))));
        d.get_dictionary_mut(page).unwrap().get_mut(b"Resources").unwrap().as_dict_mut().unwrap().set("XObject",dictionary!{"Form"=>form});set_page_content(&mut d,vec![Operation::new("Do",vec![Object::Name(b"Form".to_vec())])]);let bytes=save(&mut d);
        assert_eq!(run(&bytes).unwrap_err(),"HIGH_SENSITIVITY");let mut calls=0;assert_eq!(parse("synthetic.pdf","application/pdf",&bytes,|_,_|{calls+=1;if calls==4{Err("INTERRUPTED".into())}else{Ok(())}}).unwrap_err(),"INTERRUPTED");
    }

    #[test]
    fn pdf_text_adapter_encrypted_document_is_not_opened_with_empty_password() {
        for user_password in ["synthetic-user", ""] {
        let mut document=Document::load_mem(&pdf(&["synthetic text"],false)).unwrap();
        document.trailer.set("ID", vec![Object::string_literal("synthetic-file-id"),Object::string_literal("synthetic-file-id")]);
        let state=lopdf::encryption::EncryptionState::try_from(lopdf::encryption::EncryptionVersion::V2 {
            document:&document,owner_password:"synthetic-owner",user_password,key_length:128,
            permissions:lopdf::encryption::Permissions::all(),
        }).unwrap();
        document.encrypt(&state).unwrap();let mut bytes=Vec::new();document.save_to(&mut bytes).unwrap();
        assert_eq!(run(&bytes).unwrap_err(),"ENCRYPTED");
        }
    }
}
