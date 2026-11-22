package com.cprint.app

import androidx.core.content.FileProvider

// Android identifies local provider components by class name as well as authority.
class UpdateFileProvider : FileProvider()

class PrintDocumentFileProvider : FileProvider()
