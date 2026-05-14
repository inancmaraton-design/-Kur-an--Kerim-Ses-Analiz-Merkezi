package com.kuran.android.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuran.android.models.SpectrogramData

/**
 * 3D Spektrogram görsellendirmesi için platforma özgü (expect) tanım.
 * Desktop tarafında BufferedImage ve Swing API'leri kullanılarak çizilir.
 */
@Composable
expect fun SpectrogramRenderer(
    data: SpectrogramData,
    currentTimeSec: Float = -1f,
    modifier: Modifier = Modifier
)
