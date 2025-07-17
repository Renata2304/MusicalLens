package com.example.musicallens.ui.tutorial

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.musicallens.R
import com.example.musicallens.databinding.FragmentTutorialBinding

class TutorialFragment : Fragment() {

    private var _binding: FragmentTutorialBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTutorialBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupTutorialContent()

        return root
    }

    private fun setupTutorialContent() {
        val title1 = binding.root.findViewById<TextView>(R.id.tutorial_title_1)
        val content1 = binding.root.findViewById<TextView>(R.id.tutorial_content_1)

        val title2 = binding.root.findViewById<TextView>(R.id.tutorial_title_2)
        val content2 = binding.root.findViewById<TextView>(R.id.tutorial_content_2)

        val title3 = binding.root.findViewById<TextView>(R.id.tutorial_title_3)
        val content3 = binding.root.findViewById<TextView>(R.id.tutorial_content_3)

        val title4 = binding.root.findViewById<TextView>(R.id.tutorial_title_4)
        val content4 = binding.root.findViewById<TextView>(R.id.tutorial_content_4)

        val title5 = binding.root.findViewById<TextView>(R.id.tutorial_title_5)
        val content5 = binding.root.findViewById<TextView>(R.id.tutorial_content_5)

        val title6 = binding.root.findViewById<TextView>(R.id.tutorial_title_6)
        val content6 = binding.root.findViewById<TextView>(R.id.tutorial_content_6)

        title1.text = "1. Cum adaugi o partitură?"
        content1.text = "Din bara de navigare de jos, apasă butonul central pentru a deschide camera. " +
                "Poziționează partitura cât mai drept și fotografiaz-o. Dacă ești mulțumit de imagine, " +
                "apasă 'Oemer'. Așteaptă procesarea, iar apoi partitura se va deschide în editorul interactiv, " +
                "de unde poți vizualiza imaginile, descărca fișierul MusicXML sau transformă-l în format MIDI."

        title2.text = "2. Cum folosești fișierele MusicXML și MIDI?"
        val htmlContent2 = "Fișierele MusicXML pot fi vizualizate pe site-uri precum " +
                "<a href=\"https://www.soundslice.com/musicxml-viewer/\">soundslice.com/musicxml-viewer/</a>. " +
                "Pentru fișierele MIDI, le poți importa în aplicații specializate sau pe platforme online, " +
                "de exemplu, <a href=\"https://pianotify.com/import-midi-file/\">pianotify.com/import-midi-file</a>."

        content2.text = Html.fromHtml(htmlContent2, Html.FROM_HTML_MODE_COMPACT)

        title3.text = "3. Vizualizarea Partiturilor"
        content3.text = "După ce ai adăugat o partitură, o vei găsi în secțiunea 'Fișiere Recente'. " +
                "Apasă pe numele partiturii pentru a o deschide și vizualiza în editorul interactiv."

        title4.text = "4. Gestionarea Favoritelor"
        content4.text = "Poți adăuga o partitură la favorite apăsând pe meniul (săgeata) din dreptul " +
                "numelui partiturii și selectând 'Adaugă la Favorite'. Le vei găsi apoi în secțiunea " +
                "'Favorite' din meniul principal."

        title5.text = "5. Căutarea Partiturilor"
        content5.text = "Folosește bara de căutare din partea de sus a ecranului pentru a găsi rapid " +
                "partiturile după nume. Rezultatele se filtrează pe măsură ce tastezi."

        title6.text = "6. Opțiuni Partitură și Ștergere"
        content6.text = "Meniul (săgeata) din dreptul fiecărei partituri îți permite să o adaugi la " +
                "favorite, să o elimini din favorite sau să o ștergi complet de pe dispozitiv. " +
                "Atenție, ștergerea este permanentă!"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}