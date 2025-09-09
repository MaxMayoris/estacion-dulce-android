package com.estaciondulce.app.customviews

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.adapters.TableAdapter
import com.estaciondulce.app.models.TableColumnConfig

class TableView<T> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val recyclerView: RecyclerView
    private val headerContainer: LinearLayout
    private val previousButton: Button
    private val nextButton: Button
    private val pageIndicator: TextView

    private var currentPage = 0
    private var pageSize = 10
    private var totalPages = 1
    private var originalData = listOf<T>()
    private var paginatedAdapter: RecyclerView.Adapter<*>? = null
    private var sortedColumnIndex = -1
    private var sortedColumnDirection = true
    private var columnValueGetter: ((T, Int) -> Comparable<*>?)? = null

    init {
        orientation = VERTICAL
        inflate(context, R.layout.view_table, this)
        headerContainer = findViewById(R.id.headerContainer)
        recyclerView = findViewById(R.id.tableRecyclerView)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        pageIndicator = findViewById(R.id.pageIndicator)
        recyclerView.layoutManager = LinearLayoutManager(context)
        setupPaginationControls()
    }

    /**
     * Configures the table with headers, data, adapter, and pagination.
     */
    fun setupTable(
        columnHeaders: List<String>,
        data: List<T>,
        adapter: RecyclerView.Adapter<*>,
        pageSize: Int = 10,
        columnValueGetter: ((T, Int) -> Comparable<*>?)
    ) {
        this.pageSize = pageSize
        this.originalData = data
        this.paginatedAdapter = adapter
        this.columnValueGetter = columnValueGetter
        this.totalPages = (data.size + pageSize - 1) / pageSize

        generateHeaders(columnHeaders)
        recyclerView.adapter = paginatedAdapter
        showPage(0)
    }

    /**
     * Configures the table with column configurations, data, adapter, and pagination.
     */
    fun setupTableWithConfigs(
        columnConfigs: List<TableColumnConfig>,
        data: List<T>,
        adapter: RecyclerView.Adapter<*>,
        pageSize: Int = 10,
        columnValueGetter: ((T, Int) -> Comparable<*>?)
    ) {
        this.pageSize = pageSize
        this.originalData = data
        this.paginatedAdapter = adapter
        this.columnValueGetter = columnValueGetter
        this.totalPages = (data.size + pageSize - 1) / pageSize

        // Set column configurations in the adapter
        if (adapter is TableAdapter<*>) {
            @Suppress("UNCHECKED_CAST")
            (adapter as TableAdapter<T>).setColumnConfigs(columnConfigs)
        }

        val headers = columnConfigs.map { it.header }
        generateHeaders(headers)
        recyclerView.adapter = paginatedAdapter
        showPage(0)
    }

    /**
     * Sets up pagination controls.
     */
    private fun setupPaginationControls() {
        previousButton.setOnClickListener {
            if (currentPage > 0) showPage(currentPage - 1)
        }
        nextButton.setOnClickListener {
            if (currentPage < totalPages - 1) showPage(currentPage + 1)
        }
    }

    /**
     * Displays a page of data.
     */
    private fun showPage(page: Int) {
        currentPage = page
        val start = currentPage * pageSize
        val end = (start + pageSize).coerceAtMost(originalData.size)
        val pageData = originalData.subList(start, end)
        if (paginatedAdapter is TableAdapter<*>) {
            @Suppress("UNCHECKED_CAST")
            (paginatedAdapter as TableAdapter<T>).updateData(pageData)
        }
        pageIndicator.text = "Página ${currentPage + 1} de $totalPages"
        previousButton.visibility = if (currentPage > 0) VISIBLE else GONE
        nextButton.visibility = if (currentPage < totalPages - 1) VISIBLE else GONE
    }

    /**
     * Generates header views.
     */
    private fun generateHeaders(columnHeaders: List<String>) {
        if (headerContainer.childCount == 0) {
            columnHeaders.forEachIndexed { index, _ ->
                val textView = TextView(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        weight = 1f
                    }
                    setPadding(16, 16, 16, 16)
                    setTextColor(context.getColor(R.color.table_header_text))
                    textAlignment = TEXT_ALIGNMENT_CENTER
                    setTypeface(null, Typeface.BOLD)
                    setOnClickListener { sortByColumn(index) }
                }
                headerContainer.addView(textView)
            }
            // Add blank header for delete column.
            val deleteHeader = TextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    weight = 0.5f
                }
                setPadding(16, 16, 16, 16)
                textAlignment = TEXT_ALIGNMENT_CENTER
            }
            headerContainer.addView(deleteHeader)
        }
        columnHeaders.forEachIndexed { index, header ->
            val textView = headerContainer.getChildAt(index) as? TextView ?: return@forEachIndexed
            val isSortedColumn = index == sortedColumnIndex
            val directionIndicator = when {
                isSortedColumn && sortedColumnDirection -> " ▲"
                isSortedColumn && !sortedColumnDirection -> " ▼"
                else -> ""
            }
            textView.text = "$header$directionIndicator"
            textView.setTypeface(null, if (isSortedColumn) Typeface.BOLD else Typeface.NORMAL)
        }
        val deleteHeader = headerContainer.getChildAt(columnHeaders.size) as? TextView
        deleteHeader?.text = ""
    }

    /**
     * Sorts the table by a given column.
     */
    private fun sortByColumn(columnIndex: Int) {
        if (sortedColumnIndex == columnIndex) {
            sortedColumnDirection = !sortedColumnDirection
        } else {
            sortedColumnIndex = columnIndex
            sortedColumnDirection = true
        }
        val valueGetter = columnValueGetter ?: return
        originalData = originalData.sortedWith(Comparator { a, b ->
            @Suppress("UNCHECKED_CAST")
            val aValue = valueGetter(a, columnIndex) as? Comparable<Any>
            @Suppress("UNCHECKED_CAST")
            val bValue = valueGetter(b, columnIndex) as? Comparable<Any>
            when {
                aValue == null && bValue == null -> 0
                aValue == null -> if (sortedColumnDirection) -1 else 1
                bValue == null -> if (sortedColumnDirection) 1 else -1
                else -> if (sortedColumnDirection) aValue.compareTo(bValue) else bValue.compareTo(aValue)
            }
        })
        val headers = (0 until headerContainer.childCount).map { index ->
            (headerContainer.getChildAt(index) as TextView).text.toString().removeSuffix(" ▲").removeSuffix(" ▼")
        }
        generateHeaders(headers)
        showPage(0)
    }
}
