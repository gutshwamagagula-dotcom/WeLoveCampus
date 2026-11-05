package com.welovecampus.app.ui.more

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.welovecampus.app.databinding.FragmentMoreBinding
import com.welovecampus.app.ui.announcements.AnnouncementsActivity
import com.welovecampus.app.ui.discussions.DiscussionsActivity
import com.welovecampus.app.ui.events.EventsActivity
import com.welovecampus.app.ui.profile.ProfileActivity

/** Links to the screens that are not on the bottom bar. */
class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rowDiscussions.setOnClickListener { open(DiscussionsActivity::class.java) }
        binding.rowAnnouncements.setOnClickListener { open(AnnouncementsActivity::class.java) }
        binding.rowEvents.setOnClickListener { open(EventsActivity::class.java) }
        binding.rowProfile.setOnClickListener { open(ProfileActivity::class.java) }
    }

    private fun open(cls: Class<*>) = startActivity(Intent(requireContext(), cls))

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
