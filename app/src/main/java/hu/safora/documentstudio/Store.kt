package hu.safora.documentstudio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class Store(private val activity:Activity) {
    private val file = File(activity.filesDir,"safora_data.json")
    var docs by mutableStateOf(listOf<Doc>()); private set
    var folders by mutableStateOf(listOf("Szerződések", "Számlák", "HR", "Adminisztráció")); private set
    private var scanSessionKeys by mutableStateOf(mapOf<String,String>())
    fun scanSessionKey(name:String):String=scanSessionKeys[name]?:name
    private var vaultPasswords by mutableStateOf(mapOf<String,VaultPassword>())
    val vaultNames:List<String> get()=(listOf("Unsorted")+folders+docs.map{it.folder}).distinct()
    fun hasVaultPassword(name:String)=vaultPasswords.containsKey(name)
    fun canEnterVault(name:String)=!hasVaultPassword(name)||name in VaultSessions.unlocked
    fun canAccess(doc:Doc)=canEnterVault(doc.folder)
    fun vaultPassword(name:String)=vaultPasswords[name]
    fun unlockVault(name:String,password:CharArray):Boolean {
        if(VaultSessions.waitSeconds(name)>0)return false
        val record=vaultPasswords[name]?:return true
        val valid=VaultPasswords.verify(password,record)
        if(valid)VaultSessions.open(name) else VaultSessions.failed(name)
        return valid
    }
    fun setVaultPassword(name:String,record:VaultPassword?) {
        check(writable()&&canEnterVault(name));vaultPasswords=if(record==null)vaultPasswords-name else vaultPasswords+(name to record);save();VaultSessions.lock(name)
    }
    fun renameVault(old:String,new:String):Boolean {
        val name=new.trim();if(!writable()||!canEnterVault(old)||old=="Unsorted"||name.isBlank()||vaultNames.any{it.equals(name,true)})return false
        scanSessionKeys=(scanSessionKeys-old)+(name to scanSessionKey(old))
        folders=folders.map{if(it==old)name else it};docs=docs.map{if(it.folder==old)it.copy(folder=name) else it}
        vaultPasswords[old]?.let{vaultPasswords=(vaultPasswords-old)+(name to it)};save();VaultSessions.lock(old);VaultSessions.open(name);return true
    }
    fun deleteVault(name:String):Boolean {
        if(!writable()||name=="Unsorted"||!canEnterVault(name))return false
        if(docs.any{it.folder==name}&&!canEnterVault("Unsorted")){error(if(english)"Unlock the default vault first" else "Előbb oldd fel az alapértelmezett tárhelyet");return false}
        if(ScanFiles(activity,scanSessionKey(name)).pages().isNotEmpty()){error(if(english)"Finish or discard this vault's scan before deleting it" else "Törlés előtt fejezd be vagy vesd el a tárhely szkennelését");return false}
        scanSessionKeys=scanSessionKeys-name
        folders=folders-name;docs=docs.map{if(it.folder==name)it.copy(folder="Unsorted") else it};vaultPasswords=vaultPasswords-name;save();VaultSessions.lock(name);return true
    }
    private fun access(doc:Doc):Boolean {if(canAccess(doc))return true;error(if(english)"Unlock this vault first" else "Előbb oldd fel a tárhelyet");return false}
    var deadlines by mutableStateOf(listOf<Deadline>()); private set
    var drafts by mutableStateOf(listOf<Draft>()); private set
    var dark by mutableStateOf(true); private set
    var english by mutableStateOf(false); private set
    private val deadlineRepository = DeadlineRepository(activity)
    private var readOnly = false
    init { load() }
    fun refresh() { load(); refreshText() }
    fun refreshText() { docs=docs.map { val text=DocumentOcr.text(activity,it.id); if(text==it.text)it else it.copy(text=text) } }
    fun resumeWork() { if(readOnly)return; ReminderScheduler.channel(activity); deadlineRepository.reschedule(); docs.forEach{DocumentOcr.enqueue(activity,it)} }
    fun saveDeadline(deadline:Deadline):Boolean { if(!writable())return false;return runCatching{deadlineRepository.save(deadline);deadlines=deadlineRepository.all();true}.getOrElse{error(if(english)"Could not save reminder" else "Az emlékeztető nem menthető");false} }
    fun addScannedPdf(source:File,name:String,folder:String) {
        check(writable()&&canEnterVault(folder))
        val id=UUID.randomUUID().toString();val dir=File(activity.filesDir,"documents").apply{mkdirs()};val target=File(dir,id)
        source.copyTo(target)
        val doc=Doc(id,name.removeSuffix(".pdf")+".pdf",target.path,folder,"application/pdf",target.length(),System.currentTimeMillis())
        docs=docs+doc;save();DocumentOcr.enqueue(activity,doc)
    }
    private fun load() { runCatching {
        if (!file.exists() && !File(file.path+".bak").exists()) return
        val j=JSONObject(AtomicFile(file).openRead().bufferedReader().use{it.readText()})
        dark=j.optBoolean("dark",true); english=j.optBoolean("english",false)
        scanSessionKeys=j.optJSONObject("scanSessionKeys")?.let{keys->keys.keys().asSequence().associateWith{keys.getString(it)}}?:emptyMap()
        vaultPasswords=j.optJSONObject("vaultPasswords")?.let{v->v.keys().asSequence().associateWith{key->val x=v.getJSONObject(key);VaultPassword(x.getString("salt"),x.getString("verifier"),x.optInt("iterations",210000))}}?:emptyMap()
        folders=j.optJSONArray("folders")?.let{a->List(a.length()){a.getString(it)}} ?: folders
        docs=j.optJSONArray("docs")?.let{a->List(a.length()){val x=a.getJSONObject(it); Doc(x.getString("id"),x.getString("name"),x.getString("path"),x.getString("folder"),x.getString("type"),x.getLong("size"),x.getLong("created"),x.optBoolean("favorite"))}}?:emptyList()
        deadlines=deadlineRepository.all()
        drafts=j.optJSONArray("drafts")?.let{a->List(a.length()){val x=a.getJSONObject(it);Draft(x.getString("id"),x.getString("title"),x.getString("content"))}}?:emptyList()
    }.onFailure { error(if(english) "Could not read saved data. Original file preserved." else "A mentett adatok nem olvashatók. Az eredeti fájl megmaradt."); readOnly = true } }
    private fun writable():Boolean { if(readOnly)error(if(english)"Saved data is damaged. Restore the original file before making changes." else "A mentett adatok sérültek. Módosítás előtt állítsd helyre az eredeti fájlt.");return !readOnly }
    fun error(message:String) { Toast.makeText(activity,message,Toast.LENGTH_LONG).show() }
    private fun save() { if(readOnly) { error("Storage unavailable. Restart after restoring saved data."); return }; val j=JSONObject().put("dark",dark).put("english",english)
        .put("scanSessionKeys",JSONObject(scanSessionKeys))
        .put("vaultPasswords",JSONObject().apply{vaultPasswords.forEach{(name,record)->put(name,JSONObject().put("salt",record.salt).put("verifier",record.verifier).put("iterations",record.iterations))}})
        .put("folders",JSONArray(folders)).put("docs",JSONArray().apply{docs.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("path",it.path).put("folder",it.folder).put("type",it.type).put("size",it.size).put("created",it.created).put("favorite",it.favorite)) }})
        .put("deadlines",JSONArray().apply{deadlines.forEach{put(JSONObject().put("id",it.id).put("title",it.title).put("date",it.date).put("done",it.done))}})
        .put("drafts",JSONArray().apply{drafts.forEach{put(JSONObject().put("id",it.id).put("title",it.title).put("content",it.content))}})
        val atomic=AtomicFile(file)
        val output=atomic.startWrite()
        try { output.write(j.toString().toByteArray()); atomic.finishWrite(output) }
        catch(e:Exception) { atomic.failWrite(output); throw e } }
    fun theme(v:Boolean){if(!writable())return;dark=v;save()}; fun language(v:Boolean){if(!writable())return;english=v;save()}
    fun addFolder(s:String):Boolean {val name=s.trim();if(!writable()||name.isBlank()||vaultNames.any{it.equals(name,true)})return false;folders=folders+name;scanSessionKeys=scanSessionKeys+(name to UUID.randomUUID().toString());save();return true}
    fun toggleDeadline(id:String){if(!writable())return;deadlineRepository.toggle(id);deadlines=deadlineRepository.all()}
    fun deleteDeadline(id:String){if(!writable())return;deadlineRepository.delete(id);deadlines=deadlineRepository.all()}
    fun addDraft(title:String,content:String){if(!writable())return;if(title.isNotBlank()){drafts=drafts+Draft(UUID.randomUUID().toString(),title,content);save()}}
    fun updateDraft(id:String,title:String,content:String){if(!writable())return;drafts=drafts.map{if(it.id==id)it.copy(title=title.trim(),content=content) else it};save()}
    fun renameDoc(id:String,name:String){if(!writable()||docs.find{it.id==id}?.let{access(it)}!=true)return;if(name.isBlank())return;docs=docs.map{if(it.id==id)it.copy(name=name.trim()) else it};save()}
    fun moveDoc(id:String,folder:String){if(!writable()||!canEnterVault(folder)||docs.find{it.id==id}?.let{access(it)}!=true)return;docs=docs.map{if(it.id==id)it.copy(folder=folder) else it};save()}
    fun share(doc:Doc){if(!access(doc))return;runCatching{
        val uri=androidx.core.content.FileProvider.getUriForFile(activity,"${activity.packageName}.provider",File(doc.path))
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(doc.type).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),if(english)"Share document" else "Dokumentum megosztása"))
    }.onFailure{error(if(english)"Unable to share document" else "A dokumentum nem osztható meg")}}
    fun exportDraft(draft:Draft){if(!writable()||!canEnterVault("Unsorted")){error(if(english)"Unlock the default vault before exporting" else "Exportálás előtt oldd fel az alapértelmezett tárhelyet");return};runCatching{
        val dir=File(activity.filesDir,"documents").apply{mkdirs()};val id=UUID.randomUUID().toString();val target=File(dir,id)
        val pdf=android.graphics.pdf.PdfDocument()
        try {
            val paint=android.graphics.Paint().apply{ textSize=12f;isAntiAlias=true }
            val lines=mutableListOf<String>()
            (draft.title+"\n\n"+draft.content).lines().forEach{paragraph->
                var line="";paragraph.split(" ").forEach{word->if(paint.measureText("$line $word")>500 && line.isNotEmpty()){lines.add(line);line=word}else line=if(line.isEmpty())word else "$line $word"};lines.add(line)
            }
            lines.chunked(43).forEachIndexed{index,chunk->val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,index+1).create());chunk.forEachIndexed{i,line->page.canvas.drawText(line,44f,54f+i*17f,paint)};pdf.finishPage(page)}
            target.outputStream().use{pdf.writeTo(it)}
        } finally { pdf.close() }
        docs=docs+Doc(id,draft.title+".pdf",target.absolutePath,"Unsorted","application/pdf",target.length(),System.currentTimeMillis());save();DocumentOcr.enqueue(activity,docs.last())
        error(if(english)"PDF saved to Documents" else "PDF mentve a dokumentumtárba")
    }.onFailure{error(if(english)"PDF export failed" else "A PDF exportálása sikertelen")}}
    fun deleteDraft(id:String){if(!writable())return;drafts=drafts.filterNot{it.id==id};save()}
    fun toggleFavorite(id:String){if(!writable()||docs.find{it.id==id}?.let{access(it)}!=true)return;docs=docs.map{if(it.id==id)it.copy(favorite=!it.favorite) else it};save()}
    fun deleteDoc(id:String){if(!writable())return;val doc=docs.find{it.id==id}?:return;if(!access(doc))return
        if(File(doc.path).exists()&&!File(doc.path).delete()){error(if(english)"Could not delete file" else "A fájl nem törölhető");return}
        DocumentOcr.delete(activity,id);deadlineRepository.unlink(id);deadlines=deadlineRepository.all();docs=docs.filterNot{it.id==id};save()
    }
    fun import(uri:Uri,folder:String){if(!writable()||!canEnterVault(folder))return;runCatching{
        val resolver=activity.contentResolver
        var name="Document"; var size=0L
        resolver.query(uri,null,null,null,null)?.use{c->if(c.moveToFirst()){
            val ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ni>=0)name=c.getString(ni)?:name
            val si=c.getColumnIndex(OpenableColumns.SIZE);if(si>=0&&!c.isNull(si))size=c.getLong(si)
        }}
        val type=resolver.getType(uri)?:"application/octet-stream"
        val id=UUID.randomUUID().toString();val dir=File(activity.filesDir,"documents").apply{mkdirs()}
        val target=File(dir,id)
        try {
            require(type=="application/pdf" || type.startsWith("image/"))
            val input=resolver.openInputStream(uri) ?: throw java.io.IOException("Cannot read document")
            input.use{source->target.outputStream().use{source.copyTo(it)}}
            require(target.length()>0)
            if(type=="application/pdf")android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(target,android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use{require(it.pageCount>0)}
            else { val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(target.path,bounds);require(bounds.outWidth>0 && bounds.outHeight>0) }
        } catch(e:Exception) { target.delete();throw e }
        docs=docs+Doc(id,name,target.absolutePath,folder,type,target.length(),System.currentTimeMillis());save();DocumentOcr.enqueue(activity,docs.last())
    }.onFailure{error(if(english)"Import failed. Please choose a readable PDF or image." else "Sikertelen importálás. Válassz olvasható PDF-et vagy képet.")}}
    fun open(doc:Doc){if(!access(doc))return;runCatching{
        activity.startActivity(Intent(activity,PreviewActivity::class.java).putExtra("path",doc.path).putExtra("type",doc.type).putExtra("title",doc.name).putExtra("english",english).putExtra("dark",dark))
    }.onFailure{error(if(english)"No compatible viewer found or file unavailable" else "Nem található megjelenítő, vagy a fájl nem elérhető")}}
}

