set imagePath to "/Users/mnhkahn/code/cPrint/output/event-horizon.jpg"
set imageHfsPath to POSIX file imagePath as text

tell application "Microsoft PowerPoint"
	tell slide 4 of active presentation
		make new picture at end with properties {file name:imageHfsPath}
	end tell
end tell
