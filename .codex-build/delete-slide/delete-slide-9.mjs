import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";

const sourcePath = "/Users/mnhkahn/Library/Mobile Documents/com~apple~CloudDocs/爱子/black-hole-science-talk-west-xi-primary-v8.pptx";
const workspaceDir = "/Users/mnhkahn/code/cPrint";
const buildDir = path.join(workspaceDir, ".codex-build", "delete-slide");
const finalPath = path.join(workspaceDir, "output", "black-hole-science-talk-west-xi-primary-v8-without-slide-9-v2.pptx");
const skillDir = "/Users/mnhkahn/.codex/plugins/cache/openai-primary-runtime/presentations/26.904.11930/skills/presentations";
const runtimePython = "/Users/mnhkahn/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3";

const presentation = await PresentationFile.importPptx(await FileBlob.load(sourcePath));
if (presentation.slides.items.length !== 11) {
  throw new Error(`Expected 11 slides, found ${presentation.slides.items.length}`);
}
const target = presentation.slides.items[8];
if (target.slideNumber !== 9) {
  throw new Error(`Expected target slide 9, found ${target.slideNumber}`);
}
target.delete();
if (presentation.slides.items.length !== 10) {
  throw new Error(`Slide deletion failed; found ${presentation.slides.items.length} slides`);
}
for (let index = 1; index <= 8; index += 1) {
  const marker = `${String(index).padStart(2, "0")} / 09`;
  const candidates = presentation.slides.items[index].shapes.items.filter((shape) =>
    /^\d{2} \/ 10$/.test(String(shape.text)),
  );
  if (candidates.length !== 1) {
    throw new Error(`Expected one page marker on slide ${index + 1}, found ${candidates.length}`);
  }
  candidates[0].text.replace(String(candidates[0].text), marker);
}

const { finalizePresentation } = await import(pathToFileURL(
  path.join(skillDir, "container_tools", "artifact_tool_utils.mjs"),
).href);
const stagingDir = path.join(buildDir, "finalizer");
await fs.mkdir(stagingDir, { recursive: true });
await fs.mkdir(path.dirname(finalPath), { recursive: true });
const candidatePath = path.join(stagingDir, "candidate.pptx");
await (await PresentationFile.exportPptx(presentation)).save(candidatePath);

await finalizePresentation({
  workspaceDir,
  candidatePath,
  finalPath,
  pythonExecutable: runtimePython,
  integrityValidatorPath: path.join(skillDir, "container_tools", "inspect_presentation_package_integrity.py"),
  layoutValidatorPath: path.join(skillDir, "container_tools", "inspect_presentation_layout_geometry.py"),
  layoutArgs: ["--expected-slide-size-emu", "12192000,6858000", "--validate-bullet-geometry", "--validate-heading-fit"],
  verifyArtifactToolImport: true,
  receiptPath: path.join(stagingDir, "validation-v2.json"),
});

console.log(finalPath);
