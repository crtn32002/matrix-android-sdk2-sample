package org.matrix.android.sdk.sample.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessageInput
import com.stfalcon.chatkit.messages.MessagesListAdapter
import kotlinx.android.synthetic.main.fragment_room_detail.*
import org.matrix.android.sdk.api.NoOpMatrixCallback
import org.matrix.android.sdk.api.extensions.orTrue
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.read.ReadService
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineEventFilters
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import org.matrix.android.sdk.api.util.toMatrixItem
import org.matrix.android.sdk.sample.R
import org.matrix.android.sdk.sample.SessionHolder
import org.matrix.android.sdk.sample.utils.*


class RoomDetailFragment : Fragment(), Timeline.Listener, ToolbarConfigurable {

    companion object {

        private const val ROOM_ID_ARGS = "ROOM_ID_ARGS"

        fun newInstance(roomId: String): RoomDetailFragment {
            val args = bundleOf(
                Pair(ROOM_ID_ARGS, roomId)
            )
            return RoomDetailFragment().apply {
                arguments = args
            }
        }
    }

    private val session = SessionHolder.currentSession!!
    private var timeline: Timeline? = null
    private var room: Room? = null

    private val avatarRenderer by lazy {
        AvatarRenderer(MatrixItemColorProvider(resources))
    }

    private val imageLoader = ImageLoader { imageView, url, payload ->
        avatarRenderer.render(url, imageView)
    }

    private val adapter = MessagesListAdapter<IMessage>(session.myUserId, imageLoader)
    private val timelineEventListProcessor = TimelineEventListProcessor(adapter)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_room_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureToolbar(toolbar, displayBack = true)
        textComposer.setInputListener {
            // Sending message can be as simple as that.
            // Timeline will be automatically updated with local echo
            // and when receiving from sync so you don't have anything else to do
            room?.sendTextMessage(it)
            true
        }

        textComposer.setTypingListener(object : MessageInput.TypingListener {
            override fun onStartTyping() {
                room?.userIsTyping()
            }

            override fun onStopTyping() {
                room?.userStopsTyping()
            }
        })

        timelineEventList.setAdapter(adapter)
        timelineEventList.itemAnimator = null
        timelineEventList.addOnScrollListener(RecyclerScrollMoreListener(timelineEventList.layoutManager as LinearLayoutManager) {
            if (timeline?.hasMoreToLoad(Timeline.Direction.BACKWARDS).orTrue()) {
                timeline?.paginate(Timeline.Direction.BACKWARDS, 50)
            }
        })
        val roomId = arguments?.getString(ROOM_ID_ARGS)!!
        // You can grab a room from the session
        // If the room is not known (not received from sync) it will return null
        room = session.getRoom(roomId)

        room?.markAsRead(ReadService.MarkAsReadParams.READ_RECEIPT, NoOpMatrixCallback())

        // Create some settings to configure timeline
        val timelineSettings = TimelineSettings(
            initialSize = 30,
            filters = TimelineEventFilters(
                filterEdits = true,
                filterUseless = true,
                filterTypes = true,
                allowedTypes = TimelineDisplayableEvents.DISPLAYABLE_TYPES
            )
        )
        // Then you can retrieve a timeline from this room.
        timeline = room?.createTimeline(null, timelineSettings)?.also {
            // Don't forget to add listener and start the timeline so it start listening to changes
            it.addListener(this)
            it.start()
        }

        // You can also listen to room summary from the room
        room?.getRoomSummaryLive()?.observe(viewLifecycleOwner) { roomSummary ->
            val roomSummaryAsMatrixItem =
                roomSummary.map { it.toMatrixItem() }.getOrNull() ?: return@observe
            avatarRenderer.render(roomSummaryAsMatrixItem, toolbarAvatarImageView)
            toolbarTitleView.text = roomSummaryAsMatrixItem.getBestName()
        }

    }

    override fun onDestroyView() {
        timeline?.also {
            // Don't forget to remove listener and dispose timeline to avoid memory leaks
            it.removeAllListeners()
            it.dispose()
        }
        timeline = null
        room = null
        super.onDestroyView()
    }

    override fun onNewTimelineEvents(eventIds: List<String>) {
        // This is new event ids coming from sync
    }

    override fun onTimelineFailure(throwable: Throwable) {
        // When a failure is happening when trying to retrieve an event.
        // This is an unrecoverable error, you might want to restart the timeline
        // timeline?.restartWithEventId("")
    }

    override fun onTimelineUpdated(snapshot: List<TimelineEvent>) {
        // Each time the timeline is updated it will be called.
        // It can happens when sync returns, paginating, and updating (local echo, decryption finished...)
        // You probably want to process with DiffUtil before dispatching to your recyclerview
        timelineEventListProcessor.onNewSnapshot(snapshot)
    }


}