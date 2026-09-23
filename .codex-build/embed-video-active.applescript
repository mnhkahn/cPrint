set videoPath to "/Users/mnhkahn/code/cPrint/output/吸积盘引力透镜演示-中文配音.mp4"

with timeout of 120 seconds
	tell application "Microsoft PowerPoint"
		tell slide 5 of active presentation
			set embeddedMovie to make new media2 object at end with properties {file name:videoPath, lock aspect ratio:true, left position:68.0, top:259.0, width:262.5, height:154.0}
			set play on entry of play settings of animation settings of embeddedMovie to false
		end tell
		save active presentation
	end tell
end timeout
