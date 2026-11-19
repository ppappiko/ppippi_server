// Minimal .xlsx writer (no dependencies): text/number cells, header style, wrap, column widths, frozen header, autofilter.
// writeXlsx(path, [{ name, cols:[widths], rows:[[...]], header:true, section?: rowIndexes }])
import fs from "node:fs";
import zlib from "node:zlib";

const esc = s => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const colName = i => { let s = ""; i++; while (i > 0) { const m = (i - 1) % 26; s = String.fromCharCode(65 + m) + s; i = Math.floor((i - 1) / 26); } return s; };
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };

function zip(files) {
  const parts = [], central = []; let offset = 0;
  for (const [name, text] of files) {
    const data = Buffer.from(text, "utf8"), comp = zlib.deflateRawSync(data), nb = Buffer.from(name, "utf8"), crc = crc32(data);
    const h = Buffer.alloc(30); h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(0x0800, 6); h.writeUInt16LE(8, 8);
    h.writeUInt32LE(0, 10); h.writeUInt32LE(crc, 14); h.writeUInt32LE(comp.length, 18); h.writeUInt32LE(data.length, 22); h.writeUInt16LE(nb.length, 26); h.writeUInt16LE(0, 28);
    parts.push(h, nb, comp);
    const c = Buffer.alloc(46); c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(0x0800, 8); c.writeUInt16LE(8, 10);
    c.writeUInt32LE(0, 12); c.writeUInt32LE(crc, 16); c.writeUInt32LE(comp.length, 20); c.writeUInt32LE(data.length, 24); c.writeUInt16LE(nb.length, 28);
    c.writeUInt32LE(offset, 42); central.push(c, nb);
    offset += 30 + nb.length + comp.length;
  }
  const cd = Buffer.concat(central), e = Buffer.alloc(22);
  e.writeUInt32LE(0x06054b50, 0); e.writeUInt16LE(files.length, 8); e.writeUInt16LE(files.length, 10); e.writeUInt32LE(cd.length, 12); e.writeUInt32LE(offset, 16);
  return Buffer.concat([...parts, cd, e]);
}

// styles: 0 default, 1 header, 2 body wrap, 3 title, 4 section band, 5 status done, 6 status todo, 7 status partial
const STYLES = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="4"><font><sz val="10"/><name val="Arial"/></font><font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Arial"/></font><font><b/><sz val="14"/><name val="Arial"/></font><font><b/><sz val="10"/><name val="Arial"/></font></fonts>
<fills count="7"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF2F4A62"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE3ECF4"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFDDF2DD"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFF1CC"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFCE1D6"/></patternFill></fill></fills>
<borders count="2"><border/><border><left style="thin"><color rgb="FFBFCAD6"/></left><right style="thin"><color rgb="FFBFCAD6"/></right><top style="thin"><color rgb="FFBFCAD6"/></top><bottom style="thin"><color rgb="FFBFCAD6"/></bottom></border></borders>
<cellStyleXfs count="1"><xf/></cellStyleXfs>
<cellXfs count="8">
<xf fontId="0" applyFont="1"/>
<xf fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf fontId="0" borderId="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
<xf fontId="2" applyFont="1"/>
<xf fontId="3" fillId="3" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf fontId="0" fillId="4" borderId="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top" wrapText="1"/></xf>
<xf fontId="0" fillId="5" borderId="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top" wrapText="1"/></xf>
<xf fontId="0" fillId="6" borderId="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="top" wrapText="1"/></xf>
</cellXfs></styleSheet>`;

function sheetXml(s) {
  const out = [];
  const ncol = Math.max(...s.rows.map(r => r.length), s.cols.length);
  out.push(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">`);
  const headerRow = s.header ? (s.titleRows || 0) : -1;
  if (headerRow >= 0) out.push(`<sheetViews><sheetView workbookViewId="0"><pane ySplit="${headerRow + 1}" topLeftCell="A${headerRow + 2}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>`);
  out.push(`<cols>${s.cols.map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`).join("")}</cols><sheetData>`);
  s.rows.forEach((row, ri) => {
    const isTitle = ri < (s.titleRows || 0), isHead = ri === headerRow, isBand = row.band;
    out.push(`<row r="${ri + 1}">`);
    for (let ci = 0; ci < (isBand ? ncol : row.length); ci++) {
      const v = isBand ? (ci === 0 ? row[0] : "") : row[ci];
      let st = isTitle ? (ri === 0 ? 3 : 0) : isHead ? 1 : isBand ? 4 : 2;
      if (!isTitle && !isHead && !isBand && s.statusCol === ci) st = /완료|구현|배포|O$/.test(v) ? 5 : /진행|부분|일부|테스트/.test(v) ? 6 : v ? 7 : 2;
      const ref = colName(ci) + (ri + 1);
      if (v === undefined || v === null || v === "") { if (!isTitle) out.push(`<c r="${ref}" s="${st}"/>`); continue; }
      if (typeof v === "number") out.push(`<c r="${ref}" s="${st}"><v>${v}</v></c>`);
      else out.push(`<c r="${ref}" s="${st}" t="inlineStr"><is><t xml:space="preserve">${esc(v)}</t></is></c>`);
    }
    out.push(`</row>`);
  });
  out.push(`</sheetData>`);
  if (headerRow >= 0) out.push(`<autoFilter ref="A${headerRow + 1}:${colName(ncol - 1)}${s.rows.length}"/>`);
  const merges = s.rows.map((r, i) => r.band ? `<mergeCell ref="A${i + 1}:${colName(ncol - 1)}${i + 1}"/>` : "").filter(Boolean);
  if (merges.length) out.push(`<mergeCells count="${merges.length}">${merges.join("")}</mergeCells>`);
  out.push(`<pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0"/></worksheet>`);
  return out.join("");
}

export function writeXlsx(path, sheets) {
  const files = [
    ["[Content_Types].xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.map((_, i) => `<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join("")}</Types>`],
    ["_rels/.rels", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`],
    ["xl/workbook.xml", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.map((s, i) => `<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join("")}</sheets><definedNames>${sheets.map((s, i) => s.header ? `<definedName name="_xlnm._FilterDatabase" localSheetId="${i}" hidden="1">'${s.name}'!$A$${(s.titleRows || 0) + 1}:$${colName(Math.max(...s.rows.map(r => r.length), s.cols.length) - 1)}$${s.rows.length}</definedName>` : "").join("")}</definedNames></workbook>`],
    ["xl/_rels/workbook.xml.rels", `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.map((_, i) => `<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join("")}<Relationship Id="rId${sheets.length + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`],
    ["xl/styles.xml", STYLES],
    ...sheets.map((s, i) => [`xl/worksheets/sheet${i + 1}.xml`, sheetXml(s)]),
  ];
  fs.writeFileSync(path, zip(files));
}
