package com.example.musicallens.ui.instrument

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class InstrumentViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is instrument Fragment"
    }
    val text: LiveData<String> = _text
}