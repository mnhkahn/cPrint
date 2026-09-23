import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";

const workspaceDir = "/Users/mnhkahn/code/cPrint";
const sourcePath = "/Users/mnhkahn/Library/Mobile Documents/com~apple~CloudDocs/爱子/black-hole-science-talk-west-xi-primary-v8.pptx";
const videoFramePath = path.join(workspaceDir, ".codex-build", "accretion-video-frames", "frame-04.jpg");
const finalPath = path.join(workspaceDir, "output", "black-hole-science-talk-west-xi-primary-v8-with-accretion-disk.pptx");
const skillDir = "/Users/mnhkahn/.codex/plugins/cache/openai-primary-runtime/presentations/26.904.11930/skills/presentations";
const runtimePython = "/Users/mnhkahn/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3";

const presentation = await PresentationFile.importPptx(await FileBlob.load(sourcePath));
if (presentation.slides.items.length !== 10) throw new Error("Expected the current 10-slide source deck");

const inserted = presentation.slides.insert({ after: presentation.slides.items[3] });
const slide = inserted.slide;
slide.background.fill = "#071936";
const font = "PingFang SC";
const addText = (text, position, style = {}) => {
  const box = slide.shapes.add({
    geometry: "textbox", position, fill: "none", line: { fill: "none", width: 0 },
  });
  box.text = text;
  box.text.style = { typeface: font, color: "#FFFFFF", fontSize: 22, autoFit: "shrinkText", ...style };
  return box;
};
const addEllipse = (position, fill, line = { fill: "none", width: 0 }) =>
  slide.shapes.add({ geometry: "ellipse", position, fill, line });

addText("04 / 10", { left: 74, top: 48, width: 120, height: 28 }, { fontSize: 18, color: "#63C6FF", bold: true });
addText("吸积盘为什么看起来像一圈光环？", { left: 74, top: 80, width: 1010, height: 62 }, { fontSize: 38, bold: true });
slide.shapes.add({ geometry: "rect", position: { left: 74, top: 150, width: 112, height: 5 }, fill: "#FFC64B", line: { fill: "none", width: 0 } });

addText("它本来是一个扁平的圆盘", { left: 84, top: 190, width: 390, height: 36 }, { fontSize: 25, color: "#FFC64B", bold: true });
addText("热气体和尘埃绕着黑洞旋转，形成明亮的吸积盘。", { left: 84, top: 235, width: 420, height: 72 }, { fontSize: 22, color: "#D8E6F5" });

const diskCenterX = 815;
const diskCenterY = 385;
addEllipse({ left: diskCenterX - 210, top: diskCenterY - 55, width: 420, height: 110 }, "#F5A623", { fill: "#FFD166", width: 4 });
addEllipse({ left: diskCenterX - 165, top: diskCenterY - 38, width: 330, height: 76 }, "#FF6A3D", { fill: "#FFC64B", width: 2 });
addEllipse({ left: diskCenterX - 93, top: diskCenterY - 93, width: 186, height: 186 }, "#01040B", { fill: "#90A4C8", width: 3 });
addEllipse({ left: diskCenterX - 66, top: diskCenterY - 66, width: 132, height: 132 }, "#000000");
addText("黑洞", { left: diskCenterX - 48, top: diskCenterY - 12, width: 96, height: 28 }, { fontSize: 19, bold: true, alignment: "center" });

// Curved-looking secondary images: editable flattened ellipses above and below the disk.
addEllipse({ left: diskCenterX - 148, top: 242, width: 296, height: 64 }, "#FFF0B0", { fill: "#FFC64B", width: 3 });
addEllipse({ left: diskCenterX - 118, top: 256, width: 236, height: 35 }, "#071936", { fill: "none", width: 0 });
addEllipse({ left: diskCenterX - 148, top: 464, width: 296, height: 52 }, "#B7E8FF", { fill: "#63C6FF", width: 3 });
addEllipse({ left: diskCenterX - 118, top: 474, width: 236, height: 27 }, "#071936", { fill: "none", width: 0 });

addText("实像：直接看到的圆盘近侧", { left: 605, top: 550, width: 420, height: 30 }, { fontSize: 20, color: "#FFC64B", bold: true, alignment: "center" });
addText("虚像：远侧圆盘的光被黑洞弯到上方和下方", { left: 500, top: 596, width: 635, height: 32 }, { fontSize: 19, color: "#B7E8FF", alignment: "center" });

slide.images.add({
  blob: await fs.readFile(videoFramePath), contentType: "image/jpeg", alt: "参考视频中的纸板吸积盘演示", fit: "cover",
  position: { left: 90, top: 345, width: 350, height: 205 }, geometry: "roundRect", borderRadius: "rounded-lg",
});
addText("参考视频：纸板模型演示光线弯曲", { left: 90, top: 565, width: 360, height: 28 }, { fontSize: 17, color: "#A8D9FF", alignment: "center" });
addText("中文配音视频：吸积盘引力透镜演示-中文配音.mp4（30 秒）", { left: 120, top: 648, width: 1040, height: 25 }, { fontSize: 16, color: "#9FB6D2", alignment: "center" });
slide.speakerNotes.textFrame.setText(
  "参考视频：/Users/mnhkahn/Downloads/share_64971550236270bcb43d6c3296c01f001789813044862.mp4。\n"
  + "中文配音版本：/Users/mnhkahn/code/cPrint/output/吸积盘引力透镜演示-中文配音.mp4。\n"
  + "说明：示意图使用‘实像/虚像’帮助小学生理解直接可见的圆盘近侧，以及由引力透镜弯曲而来的远侧圆盘影像。"
);

for (const currentSlide of presentation.slides.items) {
  for (const shape of currentSlide.shapes.items) {
    const text = String(shape.text);
    const match = text.match(/^(\d{2}) \/ 09$/);
    if (!match) continue;
    const oldNumber = Number.parseInt(match[1], 10);
    const newNumber = oldNumber >= 4 ? oldNumber + 1 : oldNumber;
    shape.text.replace(text, `${String(newNumber).padStart(2, "0")} / 10`);
  }
}

const { finalizePresentation } = await import(pathToFileURL(path.join(skillDir, "container_tools", "artifact_tool_utils.mjs")).href);
const stagingDir = path.join(workspaceDir, ".codex-build", "accretion-finalizer");
await fs.mkdir(stagingDir, { recursive: true });
await fs.mkdir(path.dirname(finalPath), { recursive: true });
const candidatePath = path.join(stagingDir, "candidate.pptx");
await (await PresentationFile.exportPptx(presentation)).save(candidatePath);
await finalizePresentation({
  workspaceDir, candidatePath, finalPath, pythonExecutable: runtimePython,
  integrityValidatorPath: path.join(skillDir, "container_tools", "inspect_presentation_package_integrity.py"),
  layoutValidatorPath: path.join(skillDir, "container_tools", "inspect_presentation_layout_geometry.py"),
  layoutArgs: ["--expected-slide-size-emu", "12192000,6858000", "--validate-bullet-geometry", "--validate-heading-fit"],
  verifyArtifactToolImport: true,
  receiptPath: path.join(stagingDir, "validation.json"),
});
console.log(finalPath);
