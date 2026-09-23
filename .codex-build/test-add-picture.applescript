set imagePath to "/Users/mnhkahn/code/cPrint/output/吸积盘引力透镜示意图-中文.png"
set imageHfsPath to POSIX file imagePath as text
tell application "Microsoft PowerPoint"
	tell slide 5 of active presentation
		make new picture at end with properties {file name:imageHfsPath}
	end tell
end tell
