// Slide layouts (pptxgenjs calls these "slide masters" but each produces one layout).
const { THEME } = require("./theme.js");

function defineLayouts(pres, C) {
  // --- dark title / closing ---
  pres.defineSlideMaster({
    title: "TITLE_DARK",
    background: { color: C.text1 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.7, y: 1.9, w: 11.9, h: 1.9,
          fontSize: 40, bold: true, color: C.background1, valign: "bottom", align: "left" }, text: " " } },
      { placeholder: { options: { name: "body", type: "body", x: 0.72, y: 3.95, w: 11.9, h: 2.2,
          fontSize: 16, color: "CFD9EA", valign: "top" }, text: " " } },
    ],
  });

  // --- section divider ---
  pres.defineSlideMaster({
    title: "SECTION",
    background: { color: C.accent2 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.8, y: 2.6, w: 11.7, h: 1.3,
          fontSize: 34, bold: true, color: C.background1, valign: "middle", align: "left" }, text: " " } },
      { placeholder: { options: { name: "body", type: "body", x: 0.82, y: 3.95, w: 11.7, h: 1.2,
          fontSize: 15, color: "DCE4F5" }, text: " " } },
    ],
    slideNumber: { x: 12.5, y: 6.95, color: "AFBCD6", fontSize: 10 },
  });

  // --- light content, title at a fixed position on every content slide ---
  pres.defineSlideMaster({
    title: "CONTENT",
    background: { color: C.background1 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.6, y: 0.42, w: 12.1, h: 0.85,
          fontSize: 30, bold: true, color: C.text2, valign: "middle", align: "left" }, text: " " } },
      { text: { text: "OBMS - Intelligent Online Banking Management System", options: {
          x: 0.6, y: 6.95, w: 8.5, h: 0.3, fontSize: 9, color: C.accent6, isTextBox: true, margin: 0 } } },
    ],
    slideNumber: { x: 12.45, y: 6.95, color: C.accent6, fontSize: 10 },
  });

  // --- light content with a chart placeholder ---
  pres.defineSlideMaster({
    title: "CONTENT_CHART",
    background: { color: C.background1 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.6, y: 0.42, w: 12.1, h: 0.85,
          fontSize: 30, bold: true, color: C.text2, valign: "middle", align: "left" }, text: " " } },
      { placeholder: { options: { name: "chart", type: "chart", x: 0.6, y: 1.5, w: 7.3, h: 4.9,
          color: C.text1 }, text: " " } },
      { placeholder: { options: { name: "body", type: "body", x: 8.25, y: 1.6, w: 4.45, h: 4.7,
          fontSize: 14, color: C.text1, valign: "top" }, text: " " } },
      { text: { text: "OBMS - Intelligent Online Banking Management System", options: {
          x: 0.6, y: 6.95, w: 8.5, h: 0.3, fontSize: 9, color: C.accent6, isTextBox: true, margin: 0 } } },
    ],
    slideNumber: { x: 12.45, y: 6.95, color: C.accent6, fontSize: 10 },
  });

  // --- tinted surface, for tables ---
  pres.defineSlideMaster({
    title: "CONTENT_TINT",
    background: { color: C.background2 },
    objects: [
      { placeholder: { options: { name: "title", type: "title", x: 0.6, y: 0.42, w: 12.1, h: 0.85,
          fontSize: 30, bold: true, color: C.text2, valign: "middle", align: "left" }, text: " " } },
      { text: { text: "OBMS - Intelligent Online Banking Management System", options: {
          x: 0.6, y: 6.95, w: 8.5, h: 0.3, fontSize: 9, color: C.accent6, isTextBox: true, margin: 0 } } },
    ],
    slideNumber: { x: 12.45, y: 6.95, color: C.accent6, fontSize: 10 },
  });
}
module.exports = { defineLayouts };
