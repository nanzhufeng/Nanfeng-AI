//! Pure text-layout operations shared by local PDF text consumers. No stores or network.
use lopdf::{content::{Content,Operation},Object};
pub(crate) fn number(object:&Object)->Result<f64,String> {
    let value=match object{Object::Integer(n)=>*n as f64,Object::Real(n)=>f64::from(*n),_=>return Err("MALFORMED".into())};
    if value.is_finite(){Ok(value)}else{Err("MALFORMED".into())}
}
pub(crate) fn translated(mut line:[f64;6],x:f64,y:f64)->[f64;6] {line[4]+=x*line[0]+y*line[2];line[5]+=x*line[1]+y*line[3];line}
pub(crate) fn baseline_moved(old:[f64;6],next:[f64;6],size:f64)->bool {
    let length=old[0].hypot(old[1]);if length<1e-12{return true;}
    let perpendicular=((next[4]-old[4])*(-old[1])+(next[5]-old[5])*old[0])/length;
    let changed_direction=(old[0]*next[1]-old[1]*next[0]).abs()>1e-8;
    changed_direction || perpendicular.abs()>0.5f64.max(size.abs()*0.25)
}

/// Insert extraction-only line markers at actual text baselines. Original PDF bytes
/// are never serialized from this projection or used as a replacement attachment.
pub(crate) fn positioned_text_projection(bytes:&[u8])->Result<Vec<u8>,String> {
 let content=Content::decode(bytes).map_err(|_|"MALFORMED")?;
 let mut result=Vec::with_capacity(content.operations.len());
 let mut line=[1.0,0.0,0.0,1.0,0.0,0.0];let mut shown=None;
 let mut in_text=false;let mut size=12.0;let mut leading=0.0;let mut styles=Vec::new();
 for operation in content.operations {
  let args=&operation.operands;
  match operation.operator.as_str() {
   "q"=>styles.push((size,leading)),
   "Q"=>{if let Some(style)=styles.pop(){size=style.0;leading=style.1;}},
   "BT"=>{in_text=true;line=[1.0,0.0,0.0,1.0,0.0,0.0];shown=None;},
   "ET"=>{in_text=false;shown=None;},
   "Tf"=>{size=number(args.get(1).ok_or("MALFORMED")?)?;},
   "TL"=>{leading=number(args.first().ok_or("MALFORMED")?)?;},
   "Tm" if in_text=>{
    if args.len()!=6{return Err("MALFORMED".into());}
    line=[number(&args[0])?,number(&args[1])?,number(&args[2])?,number(&args[3])?,number(&args[4])?,number(&args[5])?];
   },
   "Td"|"TD" if in_text=>{
    if args.len()!=2{return Err("MALFORMED".into());}
    let x=number(&args[0])?;let y=number(&args[1])?;
    if operation.operator=="TD"{leading=-y;}line=translated(line,x,y);
   },
   "T*" if in_text=>{line=translated(line,0.0,-leading);shown=None;},
   "'"|"\"" if in_text=>{line=translated(line,0.0,-leading);shown=Some(line);},
   "Tj"|"TJ" if in_text=>{
    let nonempty=args.iter().any(|arg|match arg {Object::String(value,_)=>!value.is_empty(),Object::Array(values)=>values.iter().any(|value|matches!(value,Object::String(bytes,_) if !bytes.is_empty())),_=>false});
    if nonempty {
     if shown.is_some_and(|previous|baseline_moved(previous,line,size)){result.push(Operation::new("T*",vec![]));}
     shown=Some(line);
    }
   },
   _=>{},
  }
  result.push(operation);
 }
 Content{operations:result}.encode().map_err(|_|"MALFORMED".into())
}
