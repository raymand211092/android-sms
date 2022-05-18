package com.beeper.sms.app.ui.components.external

sealed class MessageLayoutType{
        sealed class DirectMessageLayoutType : MessageLayoutType(){
            object OutgoingDirectMessageLayoutType : DirectMessageLayoutType()
            object IngoingDirectMessageLayoutType : DirectMessageLayoutType()
        }
        sealed class GroupMessageLayoutType : MessageLayoutType(){
            object OutgoingGroupMessageLayoutType : GroupMessageLayoutType()
            class IngoingGroupMessageLayoutType(val clusterType: MessageClusterType) : GroupMessageLayoutType()
        }
    }
