package com.beeper.sms.app.ui.components.external

sealed class MessageClusterType {
        object Single : MessageClusterType()
        sealed class OnCluster : MessageClusterType() {
            object First : OnCluster()
            object Middle : OnCluster()
            object Last : OnCluster()
        }
    }
