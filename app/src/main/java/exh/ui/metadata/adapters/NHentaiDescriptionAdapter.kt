package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterNhBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.ui.metadata.MetadataViewController
import exh.util.SourceTagsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import java.util.Date

class NHentaiDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<NHentaiDescriptionAdapter.NHentaiDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterNhBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NHentaiDescriptionViewHolder {
        binding = DescriptionAdapterNhBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NHentaiDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: NHentaiDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class NHentaiDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is NHentaiSearchMetadata) return

            var category: String? = null
            meta.tags.filter { it.namespace == NHentaiSearchMetadata.NHENTAI_CATEGORIES_NAMESPACE }.let { tags ->
                if (tags.isNotEmpty()) category = tags.joinToString(transform = { it.name })
            }

            if (category != null) {
                val pair = when (category) {
                    "doujinshi" -> Pair(SourceTagsUtil.DOUJINSHI_COLOR, R.string.doujinshi)
                    "manga" -> Pair(SourceTagsUtil.MANGA_COLOR, R.string.manga)
                    "artistcg" -> Pair(SourceTagsUtil.ARTIST_CG_COLOR, R.string.artist_cg)
                    "gamecg" -> Pair(SourceTagsUtil.GAME_CG_COLOR, R.string.game_cg)
                    "western" -> Pair(SourceTagsUtil.WESTERN_COLOR, R.string.western)
                    "non-h" -> Pair(SourceTagsUtil.NON_H_COLOR, R.string.non_h)
                    "imageset" -> Pair(SourceTagsUtil.IMAGE_SET_COLOR, R.string.image_set)
                    "cosplay" -> Pair(SourceTagsUtil.COSPLAY_COLOR, R.string.cosplay)
                    "asianporn" -> Pair(SourceTagsUtil.ASIAN_PORN_COLOR, R.string.asian_porn)
                    "misc" -> Pair(SourceTagsUtil.MISC_COLOR, R.string.misc)
                    else -> Pair("", 0)
                }

                if (pair.first.isNotBlank()) {
                    binding.genre.setBackgroundColor(Color.parseColor(pair.first))
                    binding.genre.text = itemView.context.getString(pair.second)
                } else binding.genre.text = category
            } else binding.genre.setText(R.string.unknown)

            meta.favoritesCount?.let {
                if (it == 0L) return@let
                binding.favorites.text = it.toString()

                ContextCompat.getDrawable(itemView.context, R.drawable.ic_book_24dp)?.apply {
                    setTint(itemView.context.getResourceColor(R.attr.colorAccent))
                    setBounds(0, 0, 20.dpToPx, 20.dpToPx)
                    binding.favorites.setCompoundDrawables(this, null, null, null)
                }
            }

            binding.whenPosted.text = EX_DATE_FORMAT.format(Date((meta.uploadDate ?: 0) * 1000))

            binding.pages.text = itemView.resources.getQuantityString(R.plurals.num_pages, meta.pageImageTypes.size, meta.pageImageTypes.size)
            ContextCompat.getDrawable(itemView.context, R.drawable.ic_baseline_menu_book_24)?.apply {
                setTint(itemView.context.getResourceColor(R.attr.colorAccent))
                setBounds(0, 0, 20.dpToPx, 20.dpToPx)
                binding.pages.setCompoundDrawables(this, null, null, null)
            }

            @SuppressLint("SetTextI18n")
            binding.id.text = "#" + (meta.nhId ?: 0)

            ContextCompat.getDrawable(itemView.context, R.drawable.ic_info_24dp)?.apply {
                setTint(itemView.context.getResourceColor(R.attr.colorAccent))
                setBounds(0, 0, 20.dpToPx, 20.dpToPx)
                binding.moreInfo.setCompoundDrawables(this, null, null, null)
            }

            listOf(
                binding.favorites,
                binding.genre,
                binding.id,
                binding.pages,
                binding.whenPosted
            ).forEach { textView ->
                textView.longClicks()
                    .onEach {
                        itemView.context.copyToClipboard(
                            textView.text.toString(),
                            textView.text.toString()
                        )
                    }
                    .launchIn(scope)
            }

            binding.moreInfo.clicks()
                .onEach {
                    controller.router?.pushController(
                        MetadataViewController(
                            controller.manga
                        ).withFadeTransaction()
                    )
                }
                .launchIn(scope)
        }
    }
}
