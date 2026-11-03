// Small composition helpers so cards and callouts look identical everywhere.
function card(slide, C, { x, y, w, h, name, fill }) {
  slide.addShape("roundRect", {
    x, y, w, h, rectRadius: 0.08,
    fill: { color: fill || C.background2 },
    line: { color: "E2E8F2", width: 0.75 },
    objectName: name,
    shadow: { type: "outer", color: "9AA8BF", blur: 6, offset: 1, angle: 90, opacity: 0.18 },
  });
}

function dot(slide, C, { x, y, d, color, label, name }) {
  slide.addShape("ellipse", {
    x, y, w: d, h: d, fill: { color }, line: { color, width: 0 }, objectName: name,
  });
  slide.addText(label, {
    x, y, w: d, h: d, align: "center", valign: "middle",
    fontSize: 13, bold: true, color: "FFFFFF", isTextBox: true, margin: 0,
  });
}

function stat(slide, C, { x, y, w, value, label, color }) {
  slide.addText(value, {
    x, y, w, h: 0.95, fontSize: 44, bold: true, color: color || C.accent1,
    align: "left", valign: "bottom", isTextBox: true, margin: 0, fontFace: "Cambria",
  });
  slide.addText(label, {
    x, y: y + 0.98, w, h: 0.62, fontSize: 11, color: C.accent6,
    align: "left", valign: "top", isTextBox: true, margin: 0,
  });
}

function bullets(items) {
  return items.map((t, i) => ({
    text: t,
    options: { bullet: true, breakLine: i !== items.length - 1, paraSpaceAfter: 8 },
  }));
}
module.exports = { card, dot, stat, bullets };
