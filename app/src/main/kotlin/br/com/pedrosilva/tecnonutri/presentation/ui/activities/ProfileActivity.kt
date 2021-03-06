package br.com.pedrosilva.tecnonutri.presentation.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CollapsingToolbarLayout
import android.support.v4.content.ContextCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.widget.ImageView
import android.widget.TextView
import br.com.pedrosilva.tecnonutri.R
import br.com.pedrosilva.tecnonutri.data.repositories.ProfileRepositoryImpl
import br.com.pedrosilva.tecnonutri.domain.entities.FeedItem
import br.com.pedrosilva.tecnonutri.domain.entities.Profile
import br.com.pedrosilva.tecnonutri.domain.executor.impl.ThreadExecutor
import br.com.pedrosilva.tecnonutri.presentation.presenters.ProfilePresenter
import br.com.pedrosilva.tecnonutri.presentation.presenters.impl.ProfilePresenterImpl
import br.com.pedrosilva.tecnonutri.presentation.ui.adapters.FeedUserAdapter
import br.com.pedrosilva.tecnonutri.presentation.ui.listeners.EndlessRecyclerViewScrollListener
import br.com.pedrosilva.tecnonutri.threading.MainThreadImpl
import com.squareup.picasso.Picasso


class ProfileActivity : BaseActivity(), ProfilePresenter.View, AppBarLayout.OnOffsetChangedListener {

    private val PERCENTAGE_TO_SHOW_TITLE_AT_TOOLBAR = 0.9f

    private var userId = 0
    private var title = ""
    private var timestamp = 0
    private var nextPage = 0

    private var profilePresenter: ProfilePresenter? = null
    private var feedUserAdapter: FeedUserAdapter? = null
    private var gridLayoutManager: GridLayoutManager? = null
    private var endlessRecyclerViewScrollListener: EndlessRecyclerViewScrollListener? = null

    private var swipeRefresh: SwipeRefreshLayout? = null
    private var collapsingToolbarLayout: CollapsingToolbarLayout? = null
    private var appBar: AppBarLayout? = null
    private var toolbar: Toolbar? = null

    private var ivProfileImage: ImageView? = null
    private var tvProfileName: TextView? = null
    private var tvGeneralGoal: TextView? = null
    private var rvFeedUser: RecyclerView? = null

    private var isFirstLoad = true
    private var isTheTitleVisible = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bindElements()
        init()
    }

    private fun bindElements() {
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar) as CollapsingToolbarLayout
        toolbar = findViewById(R.id.toolbar) as Toolbar
        appBar = findViewById(R.id.appbar) as AppBarLayout
        ivProfileImage = findViewById(R.id.iv_profile_image) as ImageView
        tvProfileName = findViewById(R.id.tv_profile_name) as TextView
        tvGeneralGoal = findViewById(R.id.tv_general_goal) as TextView
        rvFeedUser = findViewById(R.id.rv_feed_user) as RecyclerView
        swipeRefresh = findViewById(R.id.swipe_refresh) as SwipeRefreshLayout
    }

    private fun init() {
        userId = intent.getIntExtra(EXTRA_PROFILE_ID, 0)
        title = intent.getStringExtra(EXTRA_PROFILE_NAME) as String

        setupToolbar(title)
        setupRecyclerView()

        tvProfileName!!.text = title
        swipeRefresh!!.setOnRefreshListener({ profilePresenter!!.refresh(userId) })

        profilePresenter = ProfilePresenterImpl(
                ThreadExecutor.getInstance(),
                MainThreadImpl.getInstance(),
                this,
                ProfileRepositoryImpl(this),
                userId
        )
    }

    private fun setupRecyclerView() {
        feedUserAdapter = FeedUserAdapter(this, this)
        gridLayoutManager = GridLayoutManager(this, 3)

        gridLayoutManager!!.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (feedUserAdapter!!.isListEnded || position < (feedUserAdapter!!.itemCount - 1)) {
                    return 1
                } else {
                    return 3
                }
            }
        }
        rvFeedUser!!.layoutManager = gridLayoutManager
        rvFeedUser!!.adapter = feedUserAdapter

        endlessRecyclerViewScrollListener = object : EndlessRecyclerViewScrollListener(gridLayoutManager) {
            override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                if (isFirstLoad) {
                    profilePresenter!!.load(userId)
                } else {
                    profilePresenter!!.loadMore(userId, nextPage, timestamp)
                }
            }
        }
        feedUserAdapter!!.setRetryClickListener({
            endlessRecyclerViewScrollListener!!.resetState()
            profilePresenter!!.loadMore(userId, nextPage, timestamp)
        })
    }

    private fun setupToolbar(title: String) {
        appBar!!.addOnOffsetChangedListener(this)

        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        collapsingToolbarLayout!!.title = title
        collapsingToolbarLayout!!.isTitleEnabled = false
        collapsingToolbarLayout!!.setExpandedTitleColor(ContextCompat.getColor(this, android.R.color.transparent))
    }

    override fun onLoadProfile(profile: Profile, feedItems: MutableList<FeedItem>, page: Int, timestamp: Int) {
        this.timestamp = timestamp
        this.nextPage = page + 1
        if (isFirstLoad || swipeRefresh!!.isRefreshing) {
            isFirstLoad = false

            Picasso.with(this)
                    .load(profile.imageUrl)
                    .placeholder(R.drawable.profile_image_placeholder)
                    .error(R.drawable.profile_image_placeholder)
                    .fit()
                    .centerCrop()
                    .into(ivProfileImage)

            collapsingToolbarLayout!!.title = profile.name
            tvProfileName!!.text = profile.name

            tvGeneralGoal!!.text = profile.generalGoal

            feedUserAdapter!!.items = feedItems
            swipeRefresh!!.isRefreshing = false
            endlessRecyclerViewScrollListener!!.resetState()
            rvFeedUser!!.clearOnScrollListeners()
            rvFeedUser!!.addOnScrollListener(endlessRecyclerViewScrollListener)
        } else {
            feedUserAdapter!!.appendItems(feedItems)
        }
        if (feedItems.count() == 0)
            feedUserAdapter!!.notifyEndList()
    }

    override fun onLoadFail(t: Throwable?) {
        swipeRefresh!!.isRefreshing = false
        val msgError = getString(R.string.fail_to_load_try_again)
        feedUserAdapter!!.notifyError(msgError)
        showError(msgError)
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout, offset: Int) {
        val maxScroll: Int = appBarLayout.totalScrollRange
        val percentage: Float = (Math.abs(offset) / maxScroll.toFloat())

        handleToolbarTitleVisibility(percentage)
    }

    private fun handleToolbarTitleVisibility(percentage: Float) {
        if (percentage >= PERCENTAGE_TO_SHOW_TITLE_AT_TOOLBAR) {
            if (!isTheTitleVisible) {
                collapsingToolbarLayout!!.isTitleEnabled = true
                isTheTitleVisible = true
            }
        } else {
            if (isTheTitleVisible) {
                collapsingToolbarLayout!!.isTitleEnabled = false
                isTheTitleVisible = false
            }
        }

    }

    companion object {
        private val EXTRA_PROFILE_ID: String = "EXTRA_PROFILE_ID"
        private val EXTRA_PROFILE_NAME: String = "EXTRA_PROFILE_NAME"

        fun getCallingIntent(context: Context, id: Int, name: String): Intent {
            val intent = Intent(context, ProfileActivity::class.java)
            intent.putExtra(EXTRA_PROFILE_ID, id)
            intent.putExtra(EXTRA_PROFILE_NAME, name)
            return intent
        }
    }
}
