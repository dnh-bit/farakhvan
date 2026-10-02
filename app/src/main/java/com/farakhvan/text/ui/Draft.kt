package com.farakhvan.text.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farakhvan.text.util.ContactItem

/** The "New SMS" draft. Lives in memory so it survives tab switches and rotation. */
object Draft {
    /** 0 = group, 1 = pasted numbers, 2 = contacts. */
    var mode by mutableIntStateOf(0)
    var groupId by mutableLongStateOf(-1L)
    var pasted by mutableStateOf("")
    var message by mutableStateOf("")

    /** SIM slot chosen for this message, or -1 to use the default from settings. */
    var simSlot by mutableIntStateOf(-1)
    val contacts = mutableStateListOf<ContactItem>()
}
