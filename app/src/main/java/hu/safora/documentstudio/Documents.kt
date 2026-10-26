package hu.safora.documentstudio

data class Doc(val id:String, val name:String, val path:String, val folder:String, val type:String, val size:Long, val created:Long, val favorite:Boolean=false, val text:String="")
data class Draft(val id:String, val title:String, val content:String)

