package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.text.SimpleDateFormat
import java.util.Locale

internal class MangaTilkisi(context: MangaLoaderContext) : MadaraParser(
    context,
    MangaSource.MANGA_TILKISI,
    "mangatilkisi.net",
    12
) {
    override val datePattern = "MMMM dd, yyyy"
    
    override val stylepage = "body,div,span,applet,object,iframe,h1,h2,h3,h4,h5,h6,p,blockquote,pre,a,abbr,acronym,address,big,cite,code,del,dfn,em,img,ins,kbd,q,s,samp,small,strike,strong,sub,sup,tt,var,b,u,i,center,dl,dt,dd,ol,ul,li,fieldset,form,label,legend,table,caption,tbody,tfoot,thead,tr,th,td,article,aside,canvas,details,embed,figure,figcaption,footer,header,hgroup,menu,nav,output,ruby,section,summary,time,mark,audio,video"
    
    override fun parseDate(date: String): Long? {
        return runCatching {
            SimpleDateFormat(datePattern, Locale.forLanguageTag("tr")).parse(date)?.time
        }.getOrNull()
    }

    override val chapterListSelector = "li.wp-manga-chapter"
    
    override suspend fun getListPage(page: Int): String {
        return if (page == 1) {
            "/manga/?m_orderby=latest"
        } else {
            "/manga/page/$page/?m_orderby=latest"
        }
    }
    
    override val contentSelector = "div.text-left"
    override val contentRemovedSelectors = setOf(
        "div.ad-container",
        "div.code-block",
        "div.sharedaddy"
    )
}
