tell application "Microsoft PowerPoint"
	tell slide 5 of active presentation
		set targetLeft to left position of picture 9 - 295.0
		set left position of shape 7 to targetLeft
		set left position of shape 8 to targetLeft
		set left position of shape 6 to targetLeft
	end tell
	save active presentation
end tell
