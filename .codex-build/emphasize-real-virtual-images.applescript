tell application "Microsoft PowerPoint"
	tell slide 5 of active presentation
		set content of text range of text frame of shape 4 to "实像：直接看到的吸积盘近侧"
		set content of text range of text frame of shape 5 to "虚像：黑洞弯曲远侧圆盘的光线，让它像一圈光环一样出现在黑洞上方和下方。"
	end tell
	save active presentation
end tell
