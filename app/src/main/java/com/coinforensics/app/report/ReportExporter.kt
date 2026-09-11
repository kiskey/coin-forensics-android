package com.coinforensics.app.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.coinforensics.app.model.AppUiState
import com.coinforensics.app.model.ImageSlot
import com.coinforensics.app.model.ReferenceSourceKind
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {
    fun createPdf(context: Context, state: AppUiState): File {
        val output = File(context.cacheDir, "coin_forensics_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        val writer = PdfWriter(doc)

        writer.heading("Coin Forensics — Evidence Report")
        writer.text("Generated ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
        writer.text("Deterministic local image analysis with provenance-aware optional online reference research. This report is not a grading-service certification and is not proof of authenticity.")
        writer.spacer(10f)

        writer.subheading("Coin metadata")
        val m = state.metadata
        writer.text("Country: ${m.country.ifBlank { "—" }}")
        writer.text("Denomination: ${m.denomination.ifBlank { "—" }}")
        writer.text("Year: ${m.year.ifBlank { "—" }}    Mint: ${m.mint.ifBlank { "—" }}")
        writer.text("Weight: ${m.weightGrams.ifBlank { "—" }} g    Diameter: ${m.diameterMm.ifBlank { "—" }} mm    Thickness: ${m.thicknessMm.ifBlank { "—" }} mm")
        if (m.notes.isNotBlank()) writer.text("Notes: ${m.notes}")
        writer.spacer(8f)

        state.images[ImageSlot.OBVERSE]?.bitmap?.let {
            writer.subheading("Obverse source image")
            writer.bitmap(it, 190f)
        }
        state.images[ImageSlot.REVERSE]?.bitmap?.let {
            writer.subheading("Reverse source image")
            writer.bitmap(it, 190f)
        }

        val selectedPack = state.referencePacks.firstOrNull { it.id == state.selectedReferencePackId }
        if (selectedPack != null) {
            writer.subheading("Exact reference pack")
            writer.text(selectedPack.displayName)
            writer.text("Catalog: ${selectedPack.catalogReferences.joinToString(" • ")}")
            selectedPack.physical.expectedWeightGrams?.let { writer.text("Reference weight: ${f(it)} g") }
            selectedPack.physical.expectedDiameterMm?.let { writer.text("Reference diameter: ${f(it)} mm") }
            writer.text(selectedPack.notes)
            if (state.referenceSamples.isNotEmpty()) {
                val genuineCount = state.referenceSamples.count { sample ->
                    selectedPack.sources.firstOrNull { it.id == sample.provenanceSourceId }?.kind in setOf(
                        ReferenceSourceKind.CERTIFIED_GENUINE,
                        ReferenceSourceKind.GENUINE_AUCTION,
                        ReferenceSourceKind.CATALOG_REFERENCE
                    )
                }
                val counterfeitCount = state.referenceSamples.size - genuineCount
                writer.text("Attached reference samples: ${state.referenceSamples.size} (genuine controls $genuineCount; counterfeit/community controls $counterfeitCount)")
                state.referenceSamples.forEach { sample ->
                    val source = selectedPack.sources.firstOrNull { it.id == sample.provenanceSourceId }
                    val role = when (source?.kind) {
                        ReferenceSourceKind.CERTIFIED_GENUINE, ReferenceSourceKind.GENUINE_AUCTION -> "certified/documented genuine control"
                        ReferenceSourceKind.CATALOG_REFERENCE -> "catalogue/open-reference control"
                        ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC -> "documented counterfeit control"
                        ReferenceSourceKind.COMMUNITY_LEAD -> "community counterfeit lead"
                        else -> "other reference"
                    }
                    writer.text("${sample.side.label} [$role]: ${source?.let { "${it.authority.label} — ${it.title}" } ?: sample.provenanceSourceId}")
                    if (sample.autoAcquired) writer.text("Acquisition: automatic licensed/reference download")
                    sample.licenseSummary?.let { writer.text("Image license/credit: $it") }
                    source?.let {
                        writer.text("Source: ${it.url}")
                        it.certificationNumber?.let { cert -> writer.text("Certification: $cert${it.gradeOrStatus?.let { grade -> " • $grade" } ?: ""}") }
                    }
                }
            } else {
                listOf(ImageSlot.REFERENCE_OBVERSE, ImageSlot.REFERENCE_REVERSE).forEach { slot ->
                    state.images[slot]?.let { image ->
                        val source = image.provenanceSourceId?.let { id -> selectedPack.sources.firstOrNull { it.id == id } }
                        writer.text("${slot.label}: ${source?.let { "${it.authority.label} — ${it.title}" } ?: "local image with no documented provenance"}")
                        source?.let {
                            writer.text("Source: ${it.url}")
                            it.certificationNumber?.let { cert -> writer.text("Certification: $cert${it.gradeOrStatus?.let { grade -> " • $grade" } ?: ""}") }
                        }
                    }
                }
            }
        }

        if (state.analysisBySlot.isNotEmpty()) {
            writer.subheading("Forensic image measurements")
            state.analysisBySlot.forEach { (slot, analysis) ->
                writer.bold(slot.label)
                val q = analysis.quality
                writer.text("Image ${q.width}×${q.height}; quality ${f(q.overallScore)}/100; sharpness ${f(q.sharpnessScore)}/100; contrast ${f(q.contrastScore)}/100; glare ${f(q.glarePercent)}%.")
                val s = analysis.surface
                writer.text("Microtexture ${f(s.microtextureEnergy)}; local variance ${f(s.localVariance)}; edge density ${f(s.edgeDensityPercent)}%; rim circularity ${s.rimCircularityScore?.let(::f) ?: "n/a"}/100; rim-pattern regularity ${s.rimPatternRegularity?.let(::f) ?: "n/a"}/100.")
                analysis.findings.forEach { finding ->
                    writer.text("[${finding.severity}] ${finding.title}: ${finding.detail}")
                }
                writer.spacer(6f)
            }
        }

        if (state.comparisonBySlot.isNotEmpty()) {
            writer.subheading("Known-genuine reference comparisons")
            state.comparisonBySlot.forEach { (slot, comparison) ->
                writer.bold(slot.label)
                writer.text("Consistency score: ${f(comparison.consistencyScore)}/100 (not an authenticity probability). Luminance ${f(comparison.luminanceCorrelation)}/100; edge geometry ${f(comparison.edgeCorrelation)}/100.")
                writer.text("Best local alignment: rotation ${f(comparison.bestRotationDegrees.toDouble())}°, scale ${f(comparison.bestScale.toDouble())}×, offset (${comparison.bestOffsetX}, ${comparison.bestOffsetY}) px.")
                writer.text(comparison.interpretation)
                writer.text("Difference map: brighter red areas represent larger normalized image differences and can include lighting, wear, photography, die-state, or true design differences.")
                writer.bitmap(comparison.differenceBitmap, 190f)
            }
        }

        state.referenceAssessment?.let { assessment ->
            writer.subheading("Exact coin / die reference assessment")
            writer.text(assessment.packName)
            assessment.evidenceConsistencyScore?.let { writer.text("Evidence consistency: ${f(it)}/100 — not an authenticity probability.") }
            assessment.provenanceStrengthScore?.let { writer.text("Reference provenance strength: ${f(it)}/100.") }
            if (assessment.provenanceSummary.isNotBlank()) writer.text(assessment.provenanceSummary)
            writer.text(assessment.interpretation)
            if (assessment.regionComparisons.isNotEmpty()) {
                writer.bold("Region-by-region comparisons")
                assessment.regionComparisons.forEach { region ->
                    writer.text("${region.side.label} — ${region.label}: ${f(region.consistencyScore)}/100; edge ${f(region.edgeScore)}; luminance ${f(region.luminanceScore)}.")
                }
            }
            if (assessment.counterfeitComparisons.isNotEmpty()) {
                writer.bold("Counterfeit-reference proximity")
                writer.text("These are source-scoped visual comparisons, not counterfeit probabilities. A low match does not prove genuine and a high match requires corroboration.")
                assessment.counterfeitComparisons.forEach { comparison ->
                    val genuine = comparison.genuineConsensusScore?.let { "; genuine baseline ${f(it)}" } ?: "; no genuine baseline"
                    val delta = comparison.deltaVsGenuine?.let { "; delta ${if (it >= 0) "+" else ""}${f(it)}" } ?: ""
                    writer.text("${comparison.side.label} — ${comparison.regionLabel}: counterfeit-reference similarity ${f(comparison.counterfeitSimilarityScore)}$genuine$delta. ${comparison.interpretation}")
                }
            }
            if (assessment.checks.isNotEmpty()) {
                writer.bold("Diagnostic checks")
                assessment.checks.forEach { check ->
                    writer.text("[${check.status}] ${check.title}: ${check.detail}")
                }
            }
            val pack = state.referencePacks.firstOrNull { it.id == assessment.packId }
            if (pack != null && assessment.sourceIdsUsed.isNotEmpty()) {
                writer.bold("Documented sources used")
                assessment.sourceIdsUsed.mapNotNull { id -> pack.sources.firstOrNull { it.id == id } }.forEach { source ->
                    writer.text("${source.authority.label} — ${source.title}")
                    writer.text(source.url)
                }
            }
        }

        writer.subheading("Interpretation rules")
        writer.text("1. Visible-light transforms only analyze information already present in the captured RGB pixels.")
        writer.text("2. UV/IR evidence is valid only when a real UV/IR image was captured. The app does not synthesize invisible wavelengths.")
        writer.text("3. A reference match can be lowered by perspective, centering, lighting, wear, die variety/state, lens distortion, or compression.")
        writer.text("4. Genuine controls and counterfeit controls are kept in separate pools. Counterfeit proximity is only a caution signal; failure to match one known fake never proves genuine.")
        writer.text("5. Weight, dimensions, edge inspection, specific gravity/XRF when appropriate, and in-hand expert authentication remain independent evidence.")

        writer.finish()
        FileOutputStream(output).use { doc.writeTo(it) }
        doc.close()
        return output
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share forensic report"))
    }

    private fun f(v: Double): String = "%.1f".format(Locale.US, v)

    private class PdfWriter(private val doc: PdfDocument) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 42f
        private val contentWidth = pageWidth - margin * 2
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10.5f }
        private val boldPaint = Paint(textPaint).apply { isFakeBoldText = true }
        private val headingPaint = Paint(boldPaint).apply { textSize = 20f }
        private val subheadingPaint = Paint(boldPaint).apply { textSize = 13.5f }
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = margin

        init { newPage() }

        fun heading(value: String) { ensure(34f); canvas!!.drawText(value, margin, y + 20f, headingPaint); y += 34f }
        fun subheading(value: String) { ensure(28f); canvas!!.drawText(value, margin, y + 16f, subheadingPaint); y += 28f }
        fun bold(value: String) { drawWrapped(value, boldPaint, 14f) }
        fun text(value: String) { drawWrapped(value, textPaint, 14f) }
        fun spacer(value: Float) { ensure(value); y += value }

        fun bitmap(bitmap: Bitmap, maxSide: Float) {
            ensure(maxSide + 14f)
            val scale = minOf(maxSide / bitmap.width, maxSide / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val left = margin + (contentWidth - w) / 2f
            val rect = RectF(left, y, left + w, y + h)
            canvas!!.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            y += h + 14f
        }

        fun finish() {
            page?.let(doc::finishPage)
            page = null
            canvas = null
        }

        private fun drawWrapped(value: String, paint: Paint, lineHeight: Float) {
            val words = value.replace("\n", " \n ").split(' ')
            var line = ""
            for (word in words) {
                if (word == "\n") {
                    drawLine(line, paint, lineHeight)
                    line = ""
                    continue
                }
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > contentWidth && line.isNotEmpty()) {
                    drawLine(line, paint, lineHeight)
                    line = word
                } else line = candidate
            }
            if (line.isNotBlank()) drawLine(line, paint, lineHeight)
        }

        private fun drawLine(line: String, paint: Paint, lineHeight: Float) {
            ensure(lineHeight + 2f)
            canvas!!.drawText(line, margin, y + paint.textSize, paint)
            y += lineHeight
        }

        private fun ensure(required: Float) {
            if (y + required < pageHeight - margin) return
            page?.let(doc::finishPage)
            newPage()
        }

        private fun newPage() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
            canvas = page!!.canvas
            canvas!!.drawColor(Color.WHITE)
            y = margin
        }
    }
}
