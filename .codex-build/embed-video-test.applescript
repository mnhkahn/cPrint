set deckPath to "/Users/mnhkahn/code/cPrint/.codex-build/embed-video-test.pptx"
set videoPath to "/Users/mnhkahn/code/cPrint/output/吸积盘引力透镜演示-中文配音.mp4"

tell application "Microsoft PowerPoint"
	activate
	open (POSIX file deckPath)
	delay 2
	tell slide 5 of active presentation
		make new media2 object at end with properties {file name:videoPath, link to file:false, save with document:true, left position:68.0, top:259.0, width:262.5, height:154.0}
	end tell
	save active presentation
	close active presentation
end tell
