package com.esri.arcgismaps.sample.findrouteintransportnetwork

import android.content.Intent
import android.os.Bundle
import com.esri.arcgismaps.sample.sampleslib.DownloaderActivity

class DownloadActivity : DownloaderActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadAndStartSample(
            // get the app name of the sample
            Intent(this, MainActivityI::class.java),
            getString(R.string.app_name),
            listOf(
                //A zip file containing an offline routing network and .tpkx basemap
                "https://arcgisruntime.maps.arcgis.com/home/item.html?id=df193653ed39449195af0c9725701dca"
            )

        )
    }
}
