package com.example.musicallens.ui.tutorial

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.musicallens.R
import com.example.musicallens.databinding.FragmentTutorialBinding
import org.w3c.dom.Text

class TutorialFragment : Fragment() {

    private var _binding: FragmentTutorialBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
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

        title1.text = getString(R.string.tutorial_title1)
        content1.text = getString(R.string.tutorial_text1)

        title2.text = getString(R.string.tutorial_title2)
        val htmlContent2 = getString(R.string.html_content2)
        content2.text = Html.fromHtml(htmlContent2, Html.FROM_HTML_MODE_COMPACT)
        content2.movementMethod = LinkMovementMethod.getInstance()

        title3.text = getString(R.string.tutorial_title3)
        content3.text = getString(R.string.tutorial_text3)

        title4.text = getString(R.string.tutorial_title4)
        content4.text = getString(R.string.tutorial_text4)

        title5.text = getString(R.string.tutorial_title5)
        content5.text = getString(R.string.tutorial_text5)

        title6.text = getString(R.string.tutorial_title6)
        content6.text = getString(R.string.tutorial_text6)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}