tell application "Microsoft PowerPoint"
	tell slide 5 of active presentation
		delete shape 17
		delete shape 16
	end tell
	save active presentation
end tell
