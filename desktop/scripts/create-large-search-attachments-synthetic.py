import sys, zipfile, pathlib
root=pathlib.Path(sys.argv[1]); root.mkdir(exist_ok=True)
def zipfile_write(name,entries):
    with zipfile.ZipFile(root/name,'w',zipfile.ZIP_DEFLATED) as z:
        for path,text in entries.items(): z.writestr(path,text)
zipfile_write('fixture.zip',{'folder/note.txt':'W13 nonempty archive body'})
zipfile_write('fixture.docx',{'[Content_Types].xml':'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>','word/document.xml':'<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>W13 nonempty DOCX body</w:t></w:r></w:p></w:body></w:document>'})
zipfile_write('fixture.xlsx',{'[Content_Types].xml':'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>','xl/workbook.xml':'<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheets><sheet name="Sheet1" sheetId="1"/></sheets></workbook>','xl/sharedStrings.xml':'<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="1" uniqueCount="1"><si><t>W13 nonempty XLSX body</t></si></sst>','xl/worksheets/sheet1.xml':'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c></row></sheetData></worksheet>'})
zipfile_write('fixture.pptx',{'[Content_Types].xml':'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>','ppt/presentation.xml':'<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldIdLst><p:sldId id="256"/></p:sldIdLst></p:presentation>','ppt/slides/slide1.xml':'<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>W13 nonempty PPTX body</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>'})
(root/'fixture.txt').write_text('W13 nonempty TXT body\n中文合成资料',encoding='utf-8')
objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>']
stream='BT /F1 24 Tf 20 100 Td (W13 nonempty PDF body) Tj ET'
objects.append(f'<< /Length {len(stream)} >>\nstream\n{stream}\nendstream')
pdf='%PDF-1.4\n'; offsets=[]
for i,obj in enumerate(objects): offsets.append(len(pdf)); pdf+=f'{i+1} 0 obj\n{obj}\nendobj\n'
xref=len(pdf); pdf+='xref\n0 6\n0000000000 65535 f \n'+''.join(f'{n:010} 00000 n \n' for n in offsets)+f'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'
(root/'fixture.pdf').write_bytes(pdf.encode('ascii'))