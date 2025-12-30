package com.example.final_app

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel: ViewModel() {
    private val _bitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val bitmaps = _bitmaps.asStateFlow()


    fun onTakePhoto(bitmap: Bitmap, isRawOn: Boolean){
        Log.d("MainViewModel", "onTakePhoto called. isRawOn: $isRawOn. Bitmap size: ${bitmap.width}x${bitmap.height}")
        _bitmaps.value += bitmap
    }
}
