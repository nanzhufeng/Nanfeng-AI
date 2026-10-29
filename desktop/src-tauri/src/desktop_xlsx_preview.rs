use crate::office_xml_compatibility_v1::{OfficeCompatibility, OfficeTextFormat};
use quick_xml::{events::Event, name::ResolveResult, NsReader};
use std::collections::{HashMap, HashSet};
use std::io::{Cursor, Read};

const MAX_ENTRIES: usize = 2_000;
const MAX_PART_BYTES: usize = 4 * 1024 * 1024;
const MAX_SELECTED_BYTES: usize = 12 * 1024 * 1024;
const MAX_OUTPUT_BYTES: usize = 2 * 1024 * 1024;

fn sheet_number(path: &str) -> Option<u32> {
    let number = path
        .strip_prefix("xl/worksheets/sheet")?
        .strip_suffix(".xml")?;
    (!number.is_empty() && number.bytes().all(|byte| byte.is_ascii_digit()))
        .then(|| number.parse().ok())
        .flatten()
}

fn read_part(zip: &mut zip::ZipArchive<Cursor<&[u8]>>, path: &str) -> Result<Vec<u8>, String> {
    let item = zip.by_name(path).map_err(|_| "XLSX 工作表不可读")?;
    let mut output = Vec::new();
    item.take(MAX_PART_BYTES as u64 + 1)
        .read_to_end(&mut output)
        .map_err(|_| "XLSX XML 读取失败")?;
    if output.len() > MAX_PART_BYTES {
        return Err("XLSX XML 超出本机解析上限".into());
    }
    // Filtering a namespace or compatibility branch must not hide invalid UTF-8.
    std::str::from_utf8(&output).map_err(|_| "XLSX 部件文字编码无效")?;
    Ok(output)
}

// Spreadsheet ST_Xstring is a UTF-16 escape layer after XML decoding. Consume
// original input once: _x005F_x0041_ is literal _x0041_, never recursively A.
fn decode_xstring(value:&str)->Result<String,String> {
    if !value.contains("_x") {return Ok(value.to_owned());}
    let mut units=Vec::with_capacity(value.len());let mut rest=value;
    while !rest.is_empty() {
        let bytes=rest.as_bytes();
        if bytes.len()>=7 && bytes[0]==b'_' && bytes[1]==b'x' && bytes[6]==b'_'
            && bytes[2..6].iter().all(u8::is_ascii_hexdigit) {
            let unit=bytes[2..6].iter().fold(0u16,|n,b| n*16+match b {b'0'..=b'9'=>(b-b'0') as u16,b'a'..=b'f'=>(b-b'a'+10) as u16,_=>(b-b'A'+10) as u16});
            units.push(unit);rest=&rest[7..];
        } else {
            let character=rest.chars().next().ok_or("XLSX 字符转义无效")?;
            let mut encoded=[0u16;2];units.extend_from_slice(character.encode_utf16(&mut encoded));rest=&rest[character.len_utf8()..];
        }
    }
    String::from_utf16(&units).map_err(|_|"XLSX 字符转义包含无效 Unicode".into())
}

fn shared_strings(xml: &[u8]) -> Result<Vec<String>, String> {
    let mut reader=NsReader::from_reader(xml);
    let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Spreadsheet);
    let mut strings=Vec::new();let mut value=String::new();let mut text_value=String::new();let mut depth=0usize;
    let mut item_depth=None;let mut text_depth=None;let mut phonetic_depth=0usize;
    loop {
        let(ns,event)=reader.read_resolved_event().map_err(|_| "XLSX 共享字符串 XML 无效")?;
        let owned=workbook_namespace(&ns);
        let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}
        match event {
            Event::DocType(_)=>return Err("XLSX 不允许外部实体".into()),
            Event::Start(event)=>{
                if owned {match event.local_name().as_ref(){
                    b"si"=>{
                        if item_depth.is_some(){return Err("XLSX 共享字符串记录不允许嵌套".into());}
                        item_depth=Some(depth+1);text_depth=None;value.clear();phonetic_depth=0;
                    }
                    b"rPh" if item_depth.is_some()=>phonetic_depth+=1,
                    b"t" if item_depth.is_some() && phonetic_depth==0=>{
                        if text_depth.is_some(){return Err("XLSX 共享字符串文字不允许嵌套".into());}
                        text_depth=Some(depth+1);text_value.clear();
                    }
                    _=>{}
                }}
                depth+=1;
            }
            Event::Empty(event) if owned && event.local_name().as_ref()==b"si"=>{
                if item_depth.is_some(){return Err("XLSX 共享字符串记录不允许嵌套".into());}
                strings.push(String::new());
            }
            Event::Text(event) if text_depth==Some(depth) && phonetic_depth==0=>text_value.push_str(&event.unescape().map_err(|_| "XLSX 共享字符串编码无效")?),
            Event::CData(event) if text_depth==Some(depth) && phonetic_depth==0=>text_value.push_str(std::str::from_utf8(event.as_ref()).map_err(|_| "XLSX 共享字符串编码无效")?),
            Event::End(event)=>{
                if owned {match event.local_name().as_ref(){
                    b"t" if text_depth==Some(depth)=>{value.push_str(&decode_xstring(&text_value)?);text_value.clear();text_depth=None;},
                    b"rPh" if phonetic_depth>0=>phonetic_depth-=1,
                    b"si" if item_depth==Some(depth)=>{strings.push(std::mem::take(&mut value));item_depth=None;}
                    _=>{}
                }}
                depth=depth.checked_sub(1).ok_or("XLSX 共享字符串 XML 无效")?;
            }
            Event::Eof=>{if depth!=0{return Err("XLSX 共享字符串 XML 不完整".into());}break;}
            _=>{}
        }
    }
    Ok(strings)
}

fn cell_position(event: &quick_xml::events::BytesStart<'_>, next_column: usize) -> Result<(usize, String), String> {
    let mut column = next_column;
    let mut cell_type = String::new();
    for attribute in event.attributes() {
        let attribute = attribute.map_err(|_| "XLSX 单元格属性无效")?;
        if attribute.key.as_ref() == b"t" {
            cell_type = attribute.unescape_value().map_err(|_| "XLSX 单元格属性无效")?.into_owned();
        } else if attribute.key.as_ref() == b"r" {
            let reference = attribute.unescape_value().map_err(|_| "XLSX 单元格坐标无效")?;
            let split = reference.bytes().take_while(u8::is_ascii_uppercase).count();
            let (letters, row) = reference.split_at(split);
            if letters.is_empty() || row.is_empty() || !row.bytes().all(|byte| byte.is_ascii_digit()) {
                return Err("XLSX 单元格坐标无效".into());
            }
            let row = row.parse::<u32>().map_err(|_| "XLSX 单元格坐标无效")?;
            if !(1..=1_048_576).contains(&row) { return Err("XLSX 单元格坐标越界".into()); }
            let mut number = 0usize;
            for byte in letters.bytes() {
                number = number.checked_mul(26).and_then(|n| n.checked_add((byte - b'A' + 1) as usize))
                    .filter(|n| *n <= 16_384).ok_or("XLSX 单元格列坐标越界")?;
            }
            column = number - 1;
        }
    }
    if column < next_column || column >= 16_384 {
        return Err("XLSX 单元格列坐标重复、倒序或越界".into());
    }
    Ok((column, cell_type))
}

fn pad_cells(cells: &mut Vec<String>, column: usize, output_bytes: &mut usize) -> Result<(), String> {
    // Include sparse-column separators in the same whole-document output budget.
    *output_bytes += column - cells.len() + usize::from(!cells.is_empty());
    if *output_bytes > MAX_OUTPUT_BYTES { return Err("XLSX 文本超过本机投影上限".into()); }
    cells.resize(column, String::new());
    Ok(())
}

fn worksheet(xml: &[u8], shared: &[String]) -> Result<String, String> {
    let mut reader=NsReader::from_reader(xml);
    let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Spreadsheet);
    let mut rows=Vec::new();let mut cells=Vec::new();let mut cell_type=String::new();let mut cell_value=String::new();
    let mut depth=0usize;let mut row_depth=None;let mut cell_depth=None;let mut value_depth=None;let mut phonetic_depth=0usize;let mut output_bytes=0;
    let mut text_value=String::new();let mut value_is_string=false;
    loop {
        let(ns,event)=reader.read_resolved_event().map_err(|_| "XLSX 工作表 XML 无效")?;
        let owned=workbook_namespace(&ns);
        let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}
        match event {
            Event::DocType(_)=>return Err("XLSX 不允许外部实体".into()),
            Event::Start(event)=>{
                if owned {match event.local_name().as_ref(){
                    b"row"=>{
                        if row_depth.is_some(){return Err("XLSX 工作表行不允许嵌套".into());}
                        row_depth=Some(depth+1);cells.clear();
                    }
                    b"c" if row_depth.is_some()=>{
                        if cell_depth.is_some(){return Err("XLSX 单元格不允许嵌套".into());}
                        let(column,kind)=cell_position(&event,cells.len())?;pad_cells(&mut cells,column,&mut output_bytes)?;
                        cell_depth=Some(depth+1);value_depth=None;cell_value.clear();cell_type=kind;phonetic_depth=0;
                    }
                    b"rPh" if cell_depth.is_some()=>phonetic_depth+=1,
                    b"v"|b"t" if cell_depth.is_some() && phonetic_depth==0=>{
                        if value_depth.is_some(){return Err("XLSX 单元格文字不允许嵌套".into());}
                        value_depth=Some(depth+1);text_value.clear();
                        value_is_string=(event.local_name().as_ref()==b"t" && cell_type=="inlineStr") || (event.local_name().as_ref()==b"v" && cell_type=="str");
                    }
                    _=>{}
                }}
                depth+=1;
            }
            Event::Text(event) if value_depth==Some(depth) && phonetic_depth==0=>text_value.push_str(&event.unescape().map_err(|_| "XLSX 单元格编码无效")?),
            Event::CData(event) if value_depth==Some(depth) && phonetic_depth==0=>text_value.push_str(std::str::from_utf8(event.as_ref()).map_err(|_| "XLSX 单元格编码无效")?),
            Event::Empty(event) if owned && row_depth.is_some() && event.local_name().as_ref()==b"c"=>{
                if cell_depth.is_some(){return Err("XLSX 单元格不允许嵌套".into());}
                let(column,_)=cell_position(&event,cells.len())?;pad_cells(&mut cells,column,&mut output_bytes)?;cells.push(String::new());
            }
            Event::End(event)=>{
                if owned {match event.local_name().as_ref(){
                    b"v"|b"t" if value_depth==Some(depth)=>{
                        if value_is_string {cell_value.push_str(&decode_xstring(&text_value)?);}else{cell_value.push_str(&text_value);}
                        text_value.clear();value_depth=None;
                    },
                    b"rPh" if phonetic_depth>0=>phonetic_depth-=1,
                    b"c" if cell_depth==Some(depth)=>{
                        let value=if cell_type=="s"{let index=cell_value.parse::<usize>().map_err(|_| "XLSX 共享字符串索引无效")?;shared.get(index).ok_or("XLSX 共享字符串索引越界")?.clone()}else{std::mem::take(&mut cell_value)};
                        output_bytes+=value.len();if output_bytes>MAX_OUTPUT_BYTES{return Err("XLSX 文本超过本机投影上限".into());}
                        cells.push(value);cell_depth=None;
                    }
                    b"row" if row_depth==Some(depth)=>{
                        output_bytes+=usize::from(!rows.is_empty());if output_bytes>MAX_OUTPUT_BYTES{return Err("XLSX 文本超过本机投影上限".into());}
                        let row=cells.join("\t").trim_end().to_owned();if !row.trim().is_empty(){rows.push(row);}row_depth=None;
                    }
                    _=>{}
                }}
                depth=depth.checked_sub(1).ok_or("XLSX 工作表 XML 无效")?;
            }
            Event::Eof=>{if depth!=0{return Err("XLSX 工作表 XML 不完整".into());}break;}
            _=>{}
        }
    }
    Ok(rows.join("\n"))
}

fn workbook_namespace(ns: &ResolveResult<'_>) -> bool {
    matches!(ns,ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/spreadsheetml/2006/main"|b"http://purl.oclc.org/ooxml/spreadsheetml/main"))
}
fn workbook_relationship_namespace(ns: &ResolveResult<'_>) -> bool {
    matches!(ns,ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/officeDocument/2006/relationships"|b"http://purl.oclc.org/ooxml/officeDocument/relationships"))
}
fn workbook_sheets(xml: &[u8]) -> Result<Vec<(String,String)>,String> {
    let mut reader=NsReader::from_reader(xml);let mut compatibility=OfficeCompatibility::new(OfficeTextFormat::Spreadsheet);let mut depth=0usize;let mut list=false;let mut seen_list=false;
    let mut sheets=Vec::new();let mut ids=HashSet::new();let mut relations=HashSet::new();
    loop {
        let (ns,event)=reader.read_resolved_event().map_err(|_| "XLSX 工作簿 XML 无效")?;
        let owned=workbook_namespace(&ns);
        let control=OfficeCompatibility::is_namespace(&ns);if !compatibility.admit(control,&event,&reader)?{continue;}
        match event {
            Event::DocType(_)=>return Err("XLSX 不允许外部实体".into()),
            Event::Start(ref e)|Event::Empty(ref e)=>{
                let local=e.local_name();
                if depth==0 && (!owned || local.as_ref()!=b"workbook"){return Err("XLSX 工作簿根无效".into());}
                if depth==1 && owned && local.as_ref()==b"sheets" {
                    if seen_list{return Err("XLSX 工作表列表重复".into());}seen_list=true;list=true;
                }
                if list && depth==2 && owned && local.as_ref()==b"sheet" {
                    let mut name=None;let mut id=None;let mut relationship=None;
                    for a in e.attributes(){
                        let a=a.map_err(|_| "XLSX 工作表属性无效")?;let (ns,key)=reader.resolve_attribute(a.key);
                        let value=a.decode_and_unescape_value(&reader).map_err(|_| "XLSX 工作表属性编码无效")?.into_owned();
                        if matches!(ns,ResolveResult::Unbound) && key.as_ref()==b"name"{name=Some(decode_xstring(&value)?);}
                        else if matches!(ns,ResolveResult::Unbound) && key.as_ref()==b"sheetId"{id=Some(value);}
                        else if workbook_relationship_namespace(&ns) && key.as_ref()==b"id" {
                            if relationship.is_some(){return Err("XLSX 工作表关系歧义".into());}relationship=Some(value);
                        }
                    }
                    let name=name.filter(|s|!s.is_empty()).ok_or("XLSX 工作表名称缺失")?;
                    let id=id.and_then(|s|s.parse::<u32>().ok()).filter(|id|*id>0).ok_or("XLSX 工作表身份无效")?;
                    let relationship=relationship.filter(|s|!s.is_empty()).ok_or("XLSX 工作表关系缺失")?;
                    if !ids.insert(id) || !relations.insert(relationship.clone()){return Err("XLSX 工作表身份重复".into());}
                    sheets.push((name,relationship));
                }
                if matches!(event,Event::Start(_)){depth+=1;}else if depth==1 && owned && local.as_ref()==b"sheets"{list=false;}
            }
            Event::End(e)=>{depth=depth.checked_sub(1).ok_or("XLSX 工作簿 XML 无效")?;if depth==1 && e.local_name().as_ref()==b"sheets"{list=false;}}
            Event::Eof=>{if depth!=0{return Err("XLSX 工作簿 XML 不完整".into());}break;}
            _=>{}
        }
    }
    if sheets.is_empty(){return Err("XLSX 没有可读取的工作表".into());}Ok(sheets)
}
struct WorkbookRelationship { kind:String,target:String,mode:Option<String> }
fn workbook_relationships(xml: &[u8]) -> Result<HashMap<String,WorkbookRelationship>,String> {
    let mut reader=NsReader::from_reader(xml);let mut depth=0usize;let mut rels=HashMap::new();
    loop {
        let (ns,event)=reader.read_resolved_event().map_err(|_| "XLSX 工作簿关系 XML 无效")?;
        let owned=matches!(ns,ResolveResult::Bound(n) if matches!(n.as_ref(),b"http://schemas.openxmlformats.org/package/2006/relationships"|b"http://purl.oclc.org/ooxml/package/relationships"));
        match event {
            Event::DocType(_)=>return Err("XLSX 不允许外部实体".into()),
            Event::Start(ref e)|Event::Empty(ref e)=>{
                if depth==0 && (!owned || e.local_name().as_ref()!=b"Relationships"){return Err("XLSX 工作簿关系根无效".into());}
                if depth==1 && owned && e.local_name().as_ref()==b"Relationship" {
                    let mut attrs=HashMap::new();
                    for a in e.attributes(){let a=a.map_err(|_| "XLSX 关系属性无效")?;attrs.insert(a.key.as_ref().to_vec(),a.decode_and_unescape_value(&reader).map_err(|_| "XLSX 关系编码无效")?.into_owned());}
                    let id=attrs.remove(b"Id".as_slice()).filter(|s|!s.is_empty()).ok_or("XLSX 关系缺少身份")?;
                    let kind=attrs.remove(b"Type".as_slice()).ok_or("XLSX 关系缺少类型")?;
                    let target=attrs.remove(b"Target".as_slice()).ok_or("XLSX 关系缺少目标")?;
                    let mode=attrs.remove(b"TargetMode".as_slice());
                    if rels.insert(id,WorkbookRelationship{kind,target,mode}).is_some(){return Err("XLSX 关系身份重复".into());}
                }
                if matches!(event,Event::Start(_)){depth+=1;}
            }
            Event::End(_)=>depth=depth.checked_sub(1).ok_or("XLSX 工作簿关系 XML 无效")?,
            Event::Eof=>{if depth!=0{return Err("XLSX 工作簿关系 XML 不完整".into());}break;}
            _=>{}
        }
    }
    Ok(rels)
}
fn relationship_kind(kind: &str, expected: &str) -> bool {
    kind.strip_prefix("http://schemas.openxmlformats.org/officeDocument/2006/relationships/")
        .or_else(||kind.strip_prefix("http://purl.oclc.org/ooxml/officeDocument/relationships/"))==Some(expected)
}
fn internal_workbook_part(rel:&WorkbookRelationship)->Result<String,String> {
    if rel.mode.as_ref().is_some_and(|m|m!="Internal"){return Err("XLSX 不读取外部工作簿部件".into());}
    let target=rel.target.as_str();
    if target.is_empty() || target.contains(['\\',':','?','#','%']) || target.starts_with("//"){return Err("XLSX 部件目标不受支持".into());}
    let mut parts=if target.starts_with('/') {Vec::new()}else{vec!["xl"]};
    for part in target.split('/') {match part {""|"."=>{},".."=>{parts.pop().ok_or("XLSX 部件目标越界")?;},v=>parts.push(v)}}
    let path=parts.join("/");if !path.ends_with(".xml"){return Err("XLSX 部件目标无效".into());}Ok(path)
}
struct WorkbookLayout { sheets:Vec<(usize,String,String)>,shared:Option<String> }
fn workbook_layout(workbook:&[u8],relationships:&[u8])->Result<WorkbookLayout,String> {
    let listed=workbook_sheets(workbook)?;let rels=workbook_relationships(relationships)?;
    let mut sheets=Vec::new();let mut targets=HashSet::new();
    for (index,(name,id)) in listed.into_iter().enumerate() {
        let rel=rels.get(&id).ok_or("XLSX 工作表关系缺失")?;
        if ["chartsheet","dialogsheet","macrosheet","intlMacrosheet"].iter().any(|kind|relationship_kind(&rel.kind,kind)){continue;}
        if !relationship_kind(&rel.kind,"worksheet"){return Err("XLSX 工作表关系类型无效".into());}
        let path=internal_workbook_part(rel)?;
        if !targets.insert(path.clone()){return Err("XLSX 工作表目标重复".into());}sheets.push((index+1,path,name));
    }
    let mut shared=None;
    for rel in rels.values().filter(|r|relationship_kind(&r.kind,"sharedStrings")) {
        if shared.is_some(){return Err("XLSX 共享字符串关系歧义".into());}shared=Some(internal_workbook_part(rel)?);
    }
    if sheets.is_empty(){return Err("XLSX 没有可读取的工作表".into());}Ok(WorkbookLayout{sheets,shared})
}

pub fn extract(bytes: &[u8]) -> Result<String, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "XLSX 文件结构无效")?;
    crate::attachment_document_text_v1::validate_raw_directory(bytes, zip.central_directory_start(), zip.len())?;
    if zip.len() > MAX_ENTRIES {
        return Err("XLSX 条目超过本机解析上限".into());
    }
    let mut sheets = Vec::new();
    let mut has_shared = false;
    let mut has_workbook=false;let mut has_relationships=false;
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "XLSX 条目不可读")?;
        if item.enclosed_name().is_none() {
            return Err("XLSX 包含不安全路径".into());
        }
        if item.is_dir() && item.size() != 0 {
            return Err("XLSX 目录条目含异常内容".into());
        }
        let path = item.name();
        has_workbook|=path=="xl/workbook.xml";
        has_relationships|=path=="xl/_rels/workbook.xml.rels";
        if path == "xl/sharedStrings.xml" {
            has_shared = true;
        } else if let Some(number) = sheet_number(path) {
            sheets.push((number, path.to_owned()));
        }
    }
    sheets.sort_by_key(|(number, _)| *number);
    let mut selected_bytes = 0;
    let (sheets,shared_path)=match (has_workbook,has_relationships) {
        (true,true)=>{
            let workbook=read_part(&mut zip,"xl/workbook.xml")?;let rels=read_part(&mut zip,"xl/_rels/workbook.xml.rels")?;
            selected_bytes+=workbook.len()+rels.len();let layout=workbook_layout(&workbook,&rels)?;
            (layout.sheets.into_iter().map(|(n,p,name)|{
                // Keep source names on one metadata line; escaped separators remain readable.
                let name=name.replace('\r',"\\r").replace('\n',"\\n").replace('\t',"\\t");
                (format!("工作表 {n}：{name}"),p)
            }).collect::<Vec<_>>(),layout.shared)
        }
        (false,false)=>(sheets.into_iter().map(|(n,p)|(format!("工作表 {n}"),p)).collect(),has_shared.then(||"xl/sharedStrings.xml".to_owned())),
        _=>return Err("XLSX 工作簿元数据不完整".into()),
    };
    if sheets.is_empty(){return Err("XLSX 没有可读取的工作表".into());}
    let shared = if let Some(path)=shared_path {
        let part = read_part(&mut zip, &path)?;
        selected_bytes += part.len();
        if selected_bytes>MAX_SELECTED_BYTES{return Err("XLSX XML 总量超出本机解析上限".into());}
        shared_strings(&part)?
    } else {
        Vec::new()
    };
    let mut output = Vec::new();
    let mut output_bytes = 0;
    for (heading, path) in sheets {
        let part = read_part(&mut zip, &path)?;
        selected_bytes += part.len();
        if selected_bytes > MAX_SELECTED_BYTES {
            return Err("XLSX XML 总量超出本机解析上限".into());
        }
        let text = worksheet(&part, &shared)?;
        if !text.trim().is_empty() {
            output_bytes += heading.len()+1+text.len()+if output.is_empty(){0}else{2};
            if output_bytes > MAX_OUTPUT_BYTES {
                return Err("XLSX 文本超过本机投影上限".into());
            }
            output.push(format!("{heading}\n{text}"));
        }
    }
    if output.is_empty() {
        return Err("XLSX 没有可读取的正文".into());
    }
    Ok(output.join("\n\n"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn workbook(shared: &str, sheet: &str) -> Vec<u8> {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (path, xml) in [
            ("xl/sharedStrings.xml", shared),
            ("xl/worksheets/sheet1.xml", sheet),
        ] {
            writer
                .start_file(path, zip::write::SimpleFileOptions::default())
                .unwrap();
            writer.write_all(xml.as_bytes()).unwrap();
        }
        writer.finish().unwrap().into_inner()
    }




    #[test]
    fn xstring_shared_text_decodes_carriage_return_and_protected_literal_once() {
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>甲_x000D_乙 _x005F_x0041_ _x0041_ &amp;</t></si></sst>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>";
        let bytes=workbook(shared,sheet);let expected="工作表 1\n甲\r乙 _x0041_ A &";
        assert_eq!(extract(&bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::XLSX).unwrap(),expected);
    }
    #[test]
    fn xstring_inline_and_cached_formula_strings_decode_after_xml_text_fragments() {
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='inlineStr'><is><t>前<![CDATA[_x00]]>41_后</t></is></c><c t='str'><f>synthetic_formula</f><v>字_x4e2d_ &amp; _x005f_x000D_</v></c></row></worksheet>";
        assert_eq!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",sheet)).unwrap(),"工作表 1\n前A后\t字中 & _x000D_");
    }
    #[test]
    fn xstring_rich_runs_never_create_an_escape_across_distinct_text_elements() {
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><r><t>_x00</t></r><r><t>41_</t></r></si></sst>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c><c t='inlineStr'><is><r><t>_x00</t></r><r><t>41_</t></r></is></c></row></worksheet>";
        assert_eq!(extract(&workbook(shared,sheet)).unwrap(),"工作表 1\n_x0041_\t_x0041_");
    }
    #[test]
    fn xstring_utf16_surrogate_pair_and_non_escape_text_are_literal() {
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>甲_xD83D__xDE00_乙 _xGGGG_ _X0041_ _x123_ _x005F_xD83D_</t></si></sst>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>";
        assert_eq!(extract(&workbook(shared,sheet)).unwrap(),"工作表 1\n甲😀乙 _xGGGG_ _X0041_ _x123_ _xD83D_");
    }
    #[test]
    fn xstring_unpaired_surrogate_is_rejected_without_replacement_or_cross_run_join() {
        for text in ["_xD83D_","_xDE00_","_xD83D_foo","_xD83D__x0041_"] {
            let shared=format!("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>{text}</t></si></sst>");
            let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>";
            let error=extract(&workbook(&shared,sheet)).unwrap_err();assert!(error.contains("转义"),"{error}");
        }
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='inlineStr'><is><r><t>_xD83D_</t></r><r><t>_xDE00_</t></r></is></c></row></worksheet>";
        assert!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",sheet)).is_err());
    }
    #[test]
    fn xstring_workbook_name_decodes_before_safe_single_line_heading() {
        let book="<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='表_x000A_次_x000D__x0009__x005F_x0041_' sheetId='1' r:id='rId1'/></sheets></workbook>";
        let rels="<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='rId1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='worksheets/sheet1.xml'/></Relationships>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c><v>42</v></c></row></worksheet>";
        assert_eq!(extract(&workbook_package(&[("xl/workbook.xml",book),("xl/_rels/workbook.xml.rels",rels),("xl/worksheets/sheet1.xml",sheet)])).unwrap(),"工作表 1：表\\n次\\r\\t_x0041_\n42");
    }
    #[test]
    fn xstring_never_decodes_numeric_cells_or_shared_string_indices() {
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>slot</t></si></sst>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c><v>_x0031_</v></c><c t='b'><v>1</v></c></row></worksheet>";
        assert_eq!(extract(&workbook(shared,sheet)).unwrap(),"工作表 1\n_x0031_\t1");
        let bad=sheet.replace("<c><v>_x0031_</v></c>","<c t='s'><v>_x0030_</v></c>");assert!(extract(&workbook(shared,&bad)).is_err());
    }

    #[test]
    fn text_namespace_shared_foreign_items_do_not_shift_real_empty_slots() {
        let shared="<s:sst xmlns:s='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:f='urn:foreign'><f:si><f:t>FOREIGN_SLOT</f:t></f:si><s:si/><s:si><s:t>甲<f:t>FOREIGN_CHILD</f:t>乙</s:t></s:si><s:si><s:t>尾</s:t></s:si></s:sst>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c><c t='s'><v>1</v></c><c t='s'><v>2</v></c></row></worksheet>";
        let bytes=workbook(shared,sheet);let expected="工作表 1\n\t甲乙\t尾";assert_eq!(extract(&bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::XLSX).unwrap(),expected);
    }
    #[test]
    fn text_namespace_foreign_rows_cells_and_values_do_not_reset_or_inject_columns() {
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:f='urn:foreign'><row><c r='A1' t='inlineStr'><is><t>甲</t></is></c><f:row><f:c r='XFE0'><f:v>FOREIGN_CELL</f:v></f:c></f:row><c r='C1'><v>4<f:v>FOREIGN_VALUE</f:v>2</v></c><f:c r='A0'/><c r='E1' t='inlineStr'><is><t>尾</t></is></c></row></worksheet>";
        assert_eq!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",sheet)).unwrap(),"工作表 1\n甲\t\t42\t\t尾");
    }
    #[test]
    fn text_namespace_strict_alias_and_rebound_prefix_keep_real_text_and_phonetic_exclusion() {
        let sheet="<s:worksheet xmlns:s='http://purl.oclc.org/ooxml/spreadsheetml/main'><s:row><s:c t='inlineStr'><s:is><s:t>始<s:t xmlns:s='urn:foreign'>FOREIGN_REBOUND</s:t>末</s:t><s:rPh><s:t>PHONETIC</s:t></s:rPh></s:is></s:c><s:c><s:v><![CDATA[42]]></s:v></s:c></s:row></s:worksheet>";
        assert_eq!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",sheet)).unwrap(),"工作表 1\n始末\t42");
    }

    #[test]
    fn text_namespace_foreign_empty_shared_records_keep_real_slots() {
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:f='urn:foreign'><f:si/><si/><si><t>TAIL</t></si></sst>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c><c t='s'><v>1</v></c></row></worksheet>";
        assert_eq!(extract(&workbook(shared,sheet)).unwrap(),"工作表 1\n\tTAIL");
    }
    #[test]
    fn text_namespace_nested_or_unclosed_owned_records_are_rejected() {
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>";
        for content in ["<row><row><c><v>42</v></c></row></row></worksheet>","<row><c><c><v>42</v></c></c></row></worksheet>","<row><c><v>4<v>2</v></v></c></row></worksheet>","<row><c><v>42</v></c></row>"] {
            let sheet=format!("<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'>{content}");assert!(extract(&workbook(shared,&sheet)).is_err());
        }
    }

    #[test]
    fn text_namespace_foreign_only_cells_are_not_readable_material() {
        let sheet="<f:worksheet xmlns:f='urn:foreign'><f:row><f:c><f:v>FOREIGN_ONLY</f:v></f:c></f:row></f:worksheet>";
        assert!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",sheet)).unwrap_err().contains("没有可读取的正文"));
    }
    #[test]
    fn text_namespace_invalid_utf8_cannot_hide_in_foreign_or_unselected_parts() {
        let shared=b"<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:f='urn:foreign'><si><t>good</t></si><f:t>\xff</f:t></sst>";
        let sheet=b"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>";
        let skipped=b"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:f='urn:foreign'><mc:AlternateContent><mc:Choice Requires='f'><row><c><v>\xff</v></c></row></mc:Choice><mc:Fallback><row><c><v>42</v></c></row></mc:Fallback></mc:AlternateContent></worksheet>";
        for (strings,rows) in [(shared.as_slice(),sheet.as_slice()),(b"<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>".as_slice(),skipped.as_slice())] {
            let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));for(path,body)in[("xl/sharedStrings.xml",strings),("xl/worksheets/sheet1.xml",rows)]{z.start_file(path,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(body).unwrap();}assert!(extract(&z.finish().unwrap().into_inner()).unwrap_err().contains("编码"));
        }
    }

    #[test] fn empty_shared_leading_slot_does_not_shift_nonempty_values_or_blank_references(){
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si/><si><t>AFTER</t></si></sst>";let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c r='A1' t='s'><v>0</v></c><c r='B1' t='s'><v>1</v></c><c r='C1' t='s'><v>0</v></c><c r='D1' t='inlineStr'><is><t>END</t></is></c></row></worksheet>";let bytes=workbook(shared,sheet);let expected="工作表 1\n\tAFTER\t\tEND";assert_eq!(extract(&bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::XLSX).unwrap(),expected);
    }
    #[test] fn empty_shared_trailing_slot_is_a_real_index_not_out_of_bounds(){
        let bytes=workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>FIRST</t></si><si/></sst>","<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>1</v></c><c t='s'><v>0</v></c></row></worksheet>");assert_eq!(extract(&bytes).unwrap(),"工作表 1\n\tFIRST");
    }
    #[test] fn empty_shared_consecutive_and_expanded_entries_each_keep_their_slot(){
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' uniqueCount='1'><si/><si></si><si><t/></si><si/><si><t>TAIL</t></si></sst>";let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c><c t='s'><v>1</v></c><c t='s'><v>2</v></c><c t='s'><v>3</v></c><c t='s'><v>4</v></c><c t='s'><v>4</v></c></row></worksheet>";assert_eq!(extract(&workbook(shared,sheet)).unwrap(),"工作表 1\n\t\t\t\tTAIL\tTAIL");
    }
    #[test] fn empty_shared_and_preserved_spaces_are_distinct_values(){
        let shared="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si/><si><t xml:space='preserve'>  </t></si><si><t>TAIL</t></si></sst>";let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c><c t='s'><v>1</v></c><c t='s'><v>2</v></c></row></worksheet>";assert_eq!(extract(&workbook(shared,sheet)).unwrap(),"工作表 1\n\t  \tTAIL");
    }
    #[test] fn empty_shared_selected_compatibility_record_keeps_its_slot_only(){
        let shared="<x:sst xmlns:x='http://purl.oclc.org/ooxml/spreadsheetml/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'><mc:AlternateContent><mc:Choice Requires='x'><x:si/></mc:Choice><mc:Fallback><x:si><x:t>UNSELECTED</x:t></x:si></mc:Fallback></mc:AlternateContent><x:si><x:t>TAIL</x:t></x:si></x:sst>";let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c><c t='s'><v>1</v></c></row></worksheet>";assert_eq!(extract(&workbook(shared,sheet)).unwrap(),"工作表 1\n\tTAIL");
    }
    #[test] fn empty_shared_active_nested_records_are_rejected_without_resetting_outer_value(){
        for inner in ["<si><t>OUTER</t><si/></si>","<si><t>OUTER</t><si><t>INNER</t></si></si>"]{let shared=format!("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'>{inner}</sst>");let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>";assert!(extract(&workbook(&shared,sheet)).is_err(),"{inner}");}
    }
    #[test] fn empty_shared_unselected_nested_records_do_not_reject_selected_content(){
        let shared="<x:sst xmlns:x='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:new='urn:synthetic-new'><x:si><x:t>KEEP</x:t><mc:AlternateContent><mc:Choice Requires='new'><x:si/></mc:Choice><mc:Fallback><x:t>_END</x:t></mc:Fallback></mc:AlternateContent></x:si></x:sst>";assert_eq!(extract(&workbook(shared,"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>")).unwrap(),"工作表 1\nKEEP_END");
    }
    #[test] fn office_alternatives_shared_string_slots_do_not_include_unselected_records(){
        let shared="<x:sst xmlns:x='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main'><mc:AlternateContent><mc:Choice Requires='p'><x:si><x:t>UNSELECTED_PRESENTATION</x:t></x:si></mc:Choice><mc:Fallback xmlns:q='http://purl.oclc.org/ooxml/spreadsheetml/main'><q:si><q:t><![CDATA[SHARED & 原文]]></q:t></q:si></mc:Fallback></mc:AlternateContent><x:si><x:t>TAIL</x:t></x:si></x:sst>";
        let bytes=workbook(shared,"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c r='A1' t='s'><v>0</v></c><c r='B1' t='s'><v>1</v></c></row></worksheet>");let expected="工作表 1\nSHARED & 原文\tTAIL";assert_eq!(extract(&bytes).unwrap(),expected);assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::XLSX).unwrap(),expected);
    }
    #[test] fn office_alternatives_worksheet_skips_invalid_unselected_cell_and_preserves_sparse_columns(){
        let sheet="<x:worksheet xmlns:x='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:new='urn:synthetic-new'><x:sheetData><x:row><mc:AlternateContent><mc:Choice Requires='new'><x:c r='A1' t='s'><x:v>999</x:v></x:c></mc:Choice><mc:Choice Requires='x'><x:c r='A1' t='inlineStr'><x:is><x:t>SELECTED</x:t></x:is></x:c></mc:Choice><mc:Fallback><x:c r='A1' t='inlineStr'><x:is><x:t>UNSELECTED_FALLBACK</x:t></x:is></x:c></mc:Fallback></mc:AlternateContent><x:c r='C1'><x:v>9</x:v></x:c></x:row></x:sheetData></x:worksheet>";assert_eq!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",sheet)).unwrap(),"工作表 1\nSELECTED\t\t9");
    }
    #[test] fn office_alternatives_workbook_manifest_keeps_selected_sheet_and_relation(){
        let mut z=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for(path,xml)in [("xl/workbook.xml","<x:workbook xmlns:x='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006' xmlns:new='urn:synthetic-new'><mc:AlternateContent><mc:Choice Requires='x'><x:sheets><x:sheet name='真实表' sheetId='1' r:id='chosen'/></x:sheets></mc:Choice><mc:Fallback><x:sheets><x:sheet name='UNSELECTED' sheetId='2' r:id='absent'/></x:sheets></mc:Fallback></mc:AlternateContent></x:workbook>"),("xl/_rels/workbook.xml.rels","<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='chosen' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='worksheets/renamed.xml'/></Relationships>"),("xl/worksheets/renamed.xml","<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='inlineStr'><is><t>BODY</t></is></c></row></worksheet>")]{z.start_file(path,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(xml.as_bytes()).unwrap();}
        assert_eq!(extract(&z.finish().unwrap().into_inner()).unwrap(),"工作表 1：真实表\nBODY");
    }
    #[test] fn office_alternatives_xlsx_malformed_requires_is_not_read_as_normal_content(){
        let shared="<x:sst xmlns:x='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'><mc:AlternateContent><mc:Choice><x:si><x:t>UNSELECTED</x:t></x:si></mc:Choice><mc:Fallback><x:si><x:t>FALLBACK</x:t></x:si></mc:Fallback></mc:AlternateContent></x:sst>";assert!(extract(&workbook(shared,"<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>0</v></c></row></worksheet>")).is_err());
    }
    #[test]
    fn phonetic_shared_string_values_preserve_base_rich_text_without_guides() {
        let bytes=workbook(
            "<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:x='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><x:si><x:r><x:t>東</x:t></x:r><x:r><x:t>京 &amp; </x:t></x:r><x:rPh sb='0' eb='2'><x:t><![CDATA[トウキョウ]]></x:t></x:rPh><x:phoneticPr fontId='0'/></x:si><x:si><x:t>かな原文</x:t></x:si></sst>",
            "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c r='A1' t='s'><v>0</v></c><c r='C1' t='s'><v>1</v></c></row></worksheet>");
        let expected="工作表 1\n東京 & \t\tかな原文";
        assert_eq!(extract(&bytes).unwrap(),expected);
        assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::XLSX).unwrap(),expected);
    }
    #[test]
    fn phonetic_inline_string_values_preserve_base_cdata_and_following_cells() {
        let bytes=workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",
            "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c r='A1' t='inlineStr'><is><r><t>中</t></r><r><t><![CDATA[国 & 😀]]></t></r><rPh sb='0' eb='1'><t>zhōng</t></rPh><rPh sb='1' eb='2'><t>guó</t></rPh></is></c><c r='B1' t='inlineStr'><is><t>zhōng 原文</t></is></c><c r='C1'><v>7</v></c></row></worksheet>");
        let expected="工作表 1\n中国 & 😀\tzhōng 原文\t7";
        assert_eq!(extract(&bytes).unwrap(),expected);
        assert_eq!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::XLSX).unwrap(),expected);
    }

    #[test]
    fn mixed_cdata_shared_inline_and_numeric_cells_are_complete() {
        let bytes = workbook(
            "<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>前 &amp; <![CDATA[<共享>&amp;😀]]> 后</t></si></sst>",
            "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v><![CDATA[0]]></v></c><c t='inlineStr'><is><t>前<![CDATA[内联尾]]>后</t></is></c><c><v><![CDATA[42]]></v></c></row></worksheet>",
        );
        assert_eq!(extract(&bytes).unwrap(), "工作表 1\n前 & <共享>&amp;😀 后\t前内联尾后\t42");
    }
    #[test]
    fn extracts_shared_inline_and_numeric_cells_without_running_formulas() {
        let bytes = workbook(
            "<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>产品 &amp; 名称</t></si></sst>",
            "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row><c t='s'><v>0</v></c><c t='inlineStr'><is><t>数量</t></is></c></row><row><c><f>1+2</f><v>3</v></c></row></sheetData></worksheet>",
        );
        assert_eq!(extract(&bytes).unwrap(), "工作表 1\n产品 & 名称\t数量\n3");
        assert!(!extract(&bytes).unwrap().contains("1+2"));
    }

    #[test]
    fn rejects_invalid_shared_index_and_external_entities() {
        assert!(extract(&workbook(
            "<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",
            "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c t='s'><v>2</v></c></row></worksheet>"
        ))
        .is_err());
        assert!(extract(&workbook(
            "<!DOCTYPE x SYSTEM 'file:///secret'><sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",
            "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>"
        ))
        .is_err());
        assert!(extract(b"not a workbook").is_err());
    }
    #[test]
    fn sparse_cell_references_preserve_headers_and_material_columns() {
        let bytes = workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>",
            "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row r='1'><c r='A1' t='inlineStr'><is><t>年份</t></is></c><c r='B1' t='inlineStr'><is><t>收入</t></is></c><c r='C1' t='inlineStr'><is><t>成本</t></is></c></row><row r='2'><c r='A2'><v>2026</v></c><c r='C2'><v>100</v></c></row><row r='3'><c r='C3'><v>200</v></c></row><row r='4'><c r='B4'/><c r='C4'><v>300</v></c></row></sheetData></worksheet>");
        let expected = "工作表 1\n年份\t收入\t成本\n2026\t\t100\n\t\t200\n\t\t300";
        assert_eq!(extract(&bytes).unwrap(), expected);
        assert_eq!(crate::attachment_document_text_v1::extract(&bytes, crate::attachment_document_text_v1::XLSX).unwrap(), expected);
    }

    #[test]
    fn sparse_coordinates_are_bounded_and_ambiguous_columns_are_rejected() {
        for cells in ["<c r='A0'><v>1</v></c>", "<c r='A1048577'><v>1</v></c>",
            "<c r='XFE1'><v>1</v></c>", "<c r='a1'><v>1</v></c>",
            "<c r='A1'><v>1</v></c><c r='A1'><v>2</v></c>",
            "<c r='B1'/><c r='A1'><v>2</v></c>"] {
            assert!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>", &format!("<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row>{cells}</row></worksheet>"))).is_err(), "{cells}");
        }
        let maximum = workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>", "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><row><c r='XFD1048576'><v>7</v></c></row></worksheet>");
        let text = extract(&maximum).unwrap();
        assert!(text.ends_with("\t7"));
        assert_eq!(text.bytes().filter(|b| *b == b'\t').count(), 16_383);
        let rows = "<row><c r='XFD1'><v>1</v></c></row>".repeat(129);
        assert!(extract(&workbook("<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'/>", &format!("<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'>{rows}</worksheet>"))).is_err(), "sparse padding must not bypass the full-text budget");
    }

    fn workbook_package(parts: &[(&str,&str)]) -> Vec<u8> {
        let mut writer=zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (name,text) in parts {writer.start_file(*name,zip::write::SimpleFileOptions::default()).unwrap();writer.write_all(text.as_bytes()).unwrap();}
        writer.finish().unwrap().into_inner()
    }
    #[test]
    fn workbook_order_names_and_referenced_strings_reach_preview_and_material() {
        let bytes=include_bytes!("../../../docs/review/20261018-windows-xlsx-workbook-order/reordered.xlsx");
        let expected="工作表 1：收入&税务\n收入100\t7\n\n工作表 2：成本表\n成本80\t\t8";
        assert_eq!(extract(bytes).unwrap(),expected);
        assert_eq!(crate::attachment_document_text_v1::extract(bytes,crate::attachment_document_text_v1::XLSX).unwrap(),expected);
    }
    #[test]
    fn strict_workbook_aliases_and_renamed_parts_preserve_hidden_cell_sheets() {
        let bytes=workbook_package(&[
            ("xl/workbook.xml","<w:workbook xmlns:w='http://purl.oclc.org/ooxml/spreadsheetml/main' xmlns:link='http://purl.oclc.org/ooxml/officeDocument/relationships'><w:sheets><w:sheet name='先' sheetId='9' link:id='a'/><w:sheet name='图表' sheetId='8' link:id='chart'/><w:sheet name='后' sheetId='7' state='hidden' link:id='b'></w:sheet></w:sheets></w:workbook>"),
            ("xl/_rels/workbook.xml.rels","<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='b' Type='http://purl.oclc.org/ooxml/officeDocument/relationships/worksheet' Target='worksheets/../worksheets/later.xml'/><Relationship Id='chart' Type='http://purl.oclc.org/ooxml/officeDocument/relationships/chartsheet' Target='chartsheets/sheet1.xml'/><Relationship Id='a' Type='http://purl.oclc.org/ooxml/officeDocument/relationships/worksheet' Target='/xl/worksheets/first.xml'/></Relationships>"),
            ("xl/worksheets/first.xml","<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row><c><v>1</v></c></row></sheetData></worksheet>"),
            ("xl/worksheets/later.xml","<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row><c><v>2</v></c></row></sheetData></worksheet>"),
            ("xl/chartsheets/sheet1.xml","<chartsheet/>")]);
        assert_eq!(extract(&bytes).unwrap(),"工作表 1：先\n1\n\n工作表 3：后\n2");
    }
    #[test]
    fn invalid_workbook_references_never_revert_to_physical_sheet_or_stale_strings() {
        let book="<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='表' sheetId='1' r:id='a'/></sheets></workbook>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row><c t='s'><v>0</v></c></row></sheetData></worksheet>";
        let strings="<sst xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><si><t>禁止错误兜底</t></si></sst>";
        let a="<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='worksheets/sheet1.xml'/>";
        let s="<Relationship Id='s' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings' Target='sharedStrings.xml'/>";
        for relations in [
            s.to_owned(),a.to_owned(),
            format!("{a}{s}{s}"),
            format!("{a}<Relationship Id='s' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings' Target='https://example.test/strings.xml' TargetMode='External'/>") ,
            format!("{s}<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='worksheets/missing.xml'/>") ,
            format!("{s}<Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='../../outside.xml'/>") ,
        ]{
            let rels=format!("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>{relations}</Relationships>");
            let bytes=workbook_package(&[("xl/workbook.xml",book),("xl/_rels/workbook.xml.rels",&rels),("xl/worksheets/sheet1.xml",sheet),("xl/sharedStrings.xml",strings)]);
            assert!(extract(&bytes).is_err(),"{relations}");
            assert!(crate::attachment_document_text_v1::extract(&bytes,crate::attachment_document_text_v1::XLSX).is_err());
        }
        assert!(extract(&workbook_package(&[("xl/workbook.xml",book),("xl/worksheets/sheet1.xml",sheet)])).is_err());
        let duplicate=book.replace("</sheets>","<sheet name='重复' sheetId='1' r:id='a'/></sheets>");
        let rels=format!("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>{a}{s}</Relationships>");
        assert!(extract(&workbook_package(&[("xl/workbook.xml",&duplicate),("xl/_rels/workbook.xml.rels",&rels),("xl/worksheets/sheet1.xml",sheet),("xl/sharedStrings.xml",strings)])).is_err());
    }
    #[test]
    fn workbook_metadata_and_sheet_names_obey_existing_resource_limits() {
        let book="<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='NAME' sheetId='1' r:id='a'/></sheets></workbook>";
        let rels="<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='worksheets/sheet1.xml'/></Relationships>";
        let sheet="<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row><c><v>1</v></c></row></sheetData></worksheet>";
        let huge=book.replace("NAME",&"x".repeat(MAX_PART_BYTES));
        assert!(extract(&workbook_package(&[("xl/workbook.xml",&huge),("xl/_rels/workbook.xml.rels",rels),("xl/worksheets/sheet1.xml",sheet)])).unwrap_err().contains("解析上限"));
        let large=book.replace("NAME",&"x".repeat(MAX_OUTPUT_BYTES));
        assert!(extract(&workbook_package(&[("xl/workbook.xml",&large),("xl/_rels/workbook.xml.rels",rels),("xl/worksheets/sheet1.xml",sheet)])).unwrap_err().contains("投影上限"));
        let dtd="<!DOCTYPE x SYSTEM 'file:///private'><workbook/>";
        assert!(extract(&workbook_package(&[("xl/workbook.xml",dtd),("xl/_rels/workbook.xml.rels",rels),("xl/worksheets/sheet1.xml",sheet)])).is_err());
    }
    #[test]
    fn sheet_name_line_separators_stay_on_one_metadata_line() {
        let bytes=workbook_package(&[
            ("xl/workbook.xml","<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='内部&#10;第二行&#13;&#9;尾部' sheetId='1' r:id='a'/></sheets></workbook>"),
            ("xl/_rels/workbook.xml.rels","<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='a' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='worksheets/sheet1.xml'/></Relationships>"),
            ("xl/worksheets/sheet1.xml","<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row><c><v>1</v></c></row></sheetData></worksheet>")]);
        assert_eq!(extract(&bytes).unwrap(),"工作表 1：内部\\n第二行\\r\\t尾部\n1");
    }

}
