package com.coinforensics.app.reference

import com.coinforensics.app.model.AutomatedTest
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.DiagnosticCategory
import com.coinforensics.app.model.DiagnosticMarker
import com.coinforensics.app.model.DiagnosticScope
import com.coinforensics.app.model.EvidenceAuthority
import com.coinforensics.app.model.NormalizedRegion
import com.coinforensics.app.model.PhysicalSpecification
import com.coinforensics.app.model.ReferencePack
import com.coinforensics.app.model.ReferenceSource
import com.coinforensics.app.model.ReferenceSourceKind
import com.coinforensics.app.model.RegionShape

/**
 * Curated, provenance-first reference packs.
 *
 * The app deliberately stores source metadata and diagnostic statements but does not bundle
 * third-party copyrighted coin photographs. A user may attach a locally saved image from one of
 * the documented sources and the app records that source id alongside the reference image.
 */
object ReferencePackRepository {
    fun builtIns(): List<ReferencePack> = listOf(greatBritainTradeDollar1911B())

    private fun greatBritainTradeDollar1911B(): ReferencePack {
        val sources = listOf(
            ReferenceSource(
                id = "pcgs-coinfacts-1911b",
                title = "PCGS CoinFacts — 1911-B Trade$ Prid-21",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.SPECIFICATION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/coinfacts/coin/1911-b-trade-prid-21/207444/97",
                notes = "Exact issue page; PCGS #207444. Use as the primary identity/catalog anchor."
            ),
            ReferenceSource(
                id = "pcgs-cert-88270736",
                title = "PCGS certified genuine control — Cert 88270736",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.CERTIFIED_GENUINE,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/cert/88270736",
                certificationNumber = "88270736",
                gradeOrStatus = "AU50",
                notes = "Exact 1911-B Prid-21 certified example. Useful as a circulated geometry control."
            ),
            ReferenceSource(
                id = "pcgs-cert-43162343",
                title = "PCGS certified genuine control — Cert 43162343",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.CERTIFIED_GENUINE,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/cert/43162343",
                certificationNumber = "43162343",
                gradeOrStatus = "MS62",
                notes = "Exact 1911-B Prid-21 certified example. Prefer for device and lettering geometry."
            ),
            ReferenceSource(
                id = "pcgs-cert-88585842",
                title = "PCGS certified genuine control — Cert 88585842",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.CERTIFIED_GENUINE,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/cert/88585842",
                certificationNumber = "88585842",
                gradeOrStatus = "XF45",
                notes = "Exact 1911-B Prid-21 certified example. Useful for checking how genuine detail looks after circulation."
            ),
            ReferenceSource(
                id = "pcgs-auction-45872285",
                title = "PCGS/Stack's Bowers certified genuine — Cert 45872285",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/auctionprices/item/1911-b-trade-prid-21/207444/-3462371324714214321",
                certificationNumber = "45872285",
                gradeOrStatus = "MS63",
                notes = "Exact 1911-B Bombay Mint, cataloged Prid-21; independently auctioned certified specimen."
            ),
            ReferenceSource(
                id = "pcgs-auction-43594463",
                title = "PCGS/Stack's Bowers certified genuine — Cert 43594463",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/auctionprices/item/1911-b-trade-prid-21/207444/-8337373610937959727",
                certificationNumber = "43594463",
                gradeOrStatus = "MS64",
                notes = "Exact 1911-B Bombay Mint, cataloged Prid-21; high-grade geometry/surface control."
            ),
            ReferenceSource(
                id = "pcgs-auction-42448304",
                title = "PCGS/Stack's Bowers certified genuine — Cert 42448304",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/auctionprices/item/1911-b-trade-prid-21/207444/8107404388127095815",
                certificationNumber = "42448304",
                gradeOrStatus = "AU58",
                notes = "Exact 1911-B Bombay Mint, cataloged Prid-21; useful circulated-to-mint-state transition control."
            ),
            ReferenceSource(
                id = "pcgs-setregistry-weight",
                title = "PCGS Set Registry — 1911-B Prid-21 specification record",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.SPECIFICATION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/setregistry/mycoinfacts/1911-b-trade-prid-21/4340135",
                certificationNumber = "87424484",
                notes = "PCGS record identifies PCGS #207444 and a listed weight of 26.96 grams."
            ),
            ReferenceSource(
                id = "greatcollections-1747409",
                title = "GreatCollections / PCGS certified genuine — Cert 46216078",
                authority = EvidenceAuthority.GREATCOLLECTIONS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.greatcollections.com/Coin/1747409/Great-Britain-1911-B-Silver-Trade-Dollar-KM-T5-PCGS-MS-62-Toned",
                certificationNumber = "46216078",
                gradeOrStatus = "PCGS MS62",
                notes = "Exact 1911-B Prid-21 certified control. GreatCollections lists 26.95 g, 39 mm and 90% silver, providing an independent physical-specification cross-check as well as another imaged genuine control."
            ),
            ReferenceSource(
                id = "pcgs-auction-41402052",
                title = "PCGS/Stack's Bowers certified genuine — Cert 41402052",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/auctionprices/item/1911-b-trade-prid-21/207444/-8636410519271820456",
                certificationNumber = "41402052",
                gradeOrStatus = "MS62",
                notes = "Exact 1911-B Prid-21 certified auction specimen; useful as another mint-state geometry control."
            ),
            ReferenceSource(
                id = "pcgs-auction-47892261",
                title = "PCGS/Stack's Bowers certified genuine — Cert 47892261",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/auctionprices/item/1911-b-trade-prid-21/207444/-5616595472291883839",
                certificationNumber = "47892261",
                gradeOrStatus = "MS62",
                notes = "Exact 1911-B Prid-21 certified auction specimen with different toning/photography; useful for reducing single-image bias."
            ),
            ReferenceSource(
                id = "pcgs-auction-87288865",
                title = "PCGS/Stack's Bowers certified genuine — Cert 87288865",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/auctionprices/item/1911-b-trade-prid-21/207444/2056001107959468897",
                certificationNumber = "87288865",
                gradeOrStatus = "XF45",
                notes = "Exact 1911-B Prid-21 certified circulated example, useful for learning genuine wear-related softening."
            ),
            ReferenceSource(
                id = "pcgs-pop-overdate",
                title = "PCGS Population Report — 1911-B and 1911/00-B varieties",
                authority = EvidenceAuthority.PCGS,
                kind = ReferenceSourceKind.SPECIFICATION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.pcgs.com/pop/detail/trade-dollars-1895-1935/4068/0?pn=1&t=4",
                notes = "PCGS separately lists 1911-B Prid-21 as #207444 and the 1911/00-B Prid-21 overdate as #207443. Use this to prevent a legitimate overdate from being mislabeled as a bad date."
            ),
            ReferenceSource(
                id = "numista-catalog-8472",
                title = "Numista catalogue — British Trade Dollar N#8472",
                authority = EvidenceAuthority.NUMISTA,
                kind = ReferenceSourceKind.SPECIFICATION,
                scope = DiagnosticScope.SERIES_WIDE,
                url = "https://en.numista.com/8472",
                notes = "Series specification lists .900 silver, 26.95 g, 39 mm, 2.7 mm thickness, reeded edge and medal alignment. Thickness is not hard-scored because another exact-year dealer specification lists 2.5 mm."
            ),
            ReferenceSource(
                id = "apmex-1911b-spec",
                title = "APMEX — 1911-B Great Britain Silver Trade Dollar BU specification",
                authority = EvidenceAuthority.APMEX,
                kind = ReferenceSourceKind.SPECIFICATION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://www.apmex.com/product/290067/1911-b-great-britain-silver-trade-dollar-bu",
                notes = "Exact-year dealer specification lists .900 silver, 39 mm diameter, 2.5 mm thickness and identifies the B mintmark in the center of Britannia's trident. Because published thickness figures conflict with Numista's 2.7 mm series value, thickness remains a manual corroborating measurement."
            ),
            ReferenceSource(
                id = "ngc-1909b-counterfeit",
                title = "NGC Counterfeit Detection — Great Britain 1909B Trade Dollar",
                authority = EvidenceAuthority.NGC,
                kind = ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC,
                scope = DiagnosticScope.SAME_TYPE_OTHER_DATE,
                url = "https://www.ngccoin.com/news/article/13925/counterfeit-detection-british-trade-dollar/",
                notes = "Same British Trade Dollar type, different date. NGC documents a fake at 26.4 g versus expected 26.96 g, grainy/overly reflective surfaces, weak ship/waves/Britannia detail, rounded lettering, and Jawi text that is too thick and rounded. Treat these as type-level warning patterns, not 1911-specific die markers."
            ),
            ReferenceSource(
                id = "ngc-counterfeit-resource",
                title = "NGC Counterfeit Detection resource",
                authority = EvidenceAuthority.NGC,
                kind = ReferenceSourceKind.AUTHENTICATION_GUIDANCE,
                scope = DiagnosticScope.GENERAL_AUTHENTICATION,
                url = "https://www.ngccoin.com/resources/counterfeit-detection/",
                notes = "NGC describes cast, transfer-die and other counterfeit methods and notes repeating depressions, weak details, incorrect weight/composition and unusual luster as possible evidence."
            ),
            ReferenceSource(
                id = "ngc-transfer-die-guidance",
                title = "NGC authentication article — repeating transferred marks",
                authority = EvidenceAuthority.NGC,
                kind = ReferenceSourceKind.AUTHENTICATION_GUIDANCE,
                scope = DiagnosticScope.GENERAL_AUTHENTICATION,
                url = "https://www.ngccoin.com/news/article/1086/coin-authenticators/",
                notes = "Explains that transfer-die counterfeits can repeat contact marks/depressions copied from a genuine host coin; repeated defects across suspect pieces are a high-value counterfeit diagnostic."
            ),
            ReferenceSource(
                id = "stacks-2021-hk-catalog",
                title = "Stack's Bowers September 2021 Hong Kong catalog — multiple 1911-B certified examples",
                authority = EvidenceAuthority.STACKS_BOWERS,
                kind = ReferenceSourceKind.GENUINE_AUCTION,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://media.stacksbowers.com/VirtualCatalogs/CatalogLibrary/SBP_Sept2021HK_CoinPart2_Int_WebLR.pdf",
                notes = "Catalog contains multiple PCGS/NGC-certified 1911-B Prid-21 examples across grades and describes genuine strike/luster variation. Useful for avoiding false alarms caused by die state, circulation, toning or cleaning."
            ),
            ReferenceSource(
                id = "numista-forum-1911b-fakes",
                title = "Numista community discussion — British Trade dollars and fakes",
                authority = EvidenceAuthority.NUMISTA,
                kind = ReferenceSourceKind.COMMUNITY_LEAD,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://en.numista.com/forum/topic52795.html",
                notes = "Community lead indicating 1911-B is frequently encountered as a fake. Not authoritative enough to drive an automated fail by itself; use only as a lead to seek professional diagnostics."
            ),
            ReferenceSource(
                id = "numista-forum-1911-transfer",
                title = "Numista community case — British Trade Dollar 1911 suspected transfer-die fake",
                authority = EvidenceAuthority.NUMISTA,
                kind = ReferenceSourceKind.COMMUNITY_LEAD,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                url = "https://en.numista.com/forum/topic79883.html",
                notes = "Exact-year community case with photographs and discussion of wrong color, extra/imprinted-looking field detail near the shield, transfer-die-like detail, and incorrect diameter/weight. Treat as a documented community comparison lead only, never as an authoritative counterfeit certification."
            )
        )

        val regions = listOf(
            NormalizedRegion(
                id = "obverse-full",
                label = "Britannia side — complete design",
                side = CoinSide.OBVERSE,
                shape = RegionShape.FULL_COIN,
                description = "Whole-side geometry after local registration."
            ),
            NormalizedRegion(
                id = "obverse-date",
                label = "Date geometry",
                side = CoinSide.OBVERSE,
                shape = RegionShape.RECT,
                left = 0.32,
                top = 0.78,
                right = 0.68,
                bottom = 0.96,
                description = "Date position, digit proportions and local relief boundaries."
            ),
            NormalizedRegion(
                id = "obverse-trident-mintmark",
                label = "Trident and B mintmark",
                side = CoinSide.OBVERSE,
                shape = RegionShape.RECT,
                left = 0.14,
                top = 0.08,
                right = 0.42,
                bottom = 0.56,
                description = "Trident tine geometry and the small Bombay B mintmark area; compare shape and placement against several genuine controls."
            ),
            NormalizedRegion(
                id = "obverse-ship-waves",
                label = "Merchant ship and waves",
                side = CoinSide.OBVERSE,
                shape = RegionShape.RECT,
                left = 0.12,
                top = 0.47,
                right = 0.50,
                bottom = 0.79,
                description = "Ship, masts/flags and wave relief where documented British Trade Dollar fakes may lose detail."
            ),
            NormalizedRegion(
                id = "obverse-shield-field",
                label = "Shield and adjacent field",
                side = CoinSide.OBVERSE,
                shape = RegionShape.RECT,
                left = 0.54,
                top = 0.40,
                right = 0.86,
                bottom = 0.80,
                description = "Shield boundary and neighboring field used for exact-year community transfer-die-like artifact review."
            ),
            NormalizedRegion(
                id = "obverse-relief",
                label = "Britannia / principal relief",
                side = CoinSide.OBVERSE,
                shape = RegionShape.RECT,
                left = 0.18,
                top = 0.18,
                right = 0.80,
                bottom = 0.80,
                description = "Principal device and relief-boundary comparison."
            ),
            NormalizedRegion(
                id = "obverse-rim",
                label = "Obverse rim and denticles",
                side = CoinSide.OBVERSE,
                shape = RegionShape.ANNULUS,
                innerRadius = 0.78,
                outerRadius = 0.97,
                description = "Peripheral rim/denticle geometry; sensitive to framing and perspective."
            ),
            NormalizedRegion(
                id = "reverse-full",
                label = "Script side — complete design",
                side = CoinSide.REVERSE,
                shape = RegionShape.FULL_COIN,
                description = "Whole-side geometry after local registration."
            ),
            NormalizedRegion(
                id = "reverse-center",
                label = "Central Chinese characters / ornament",
                side = CoinSide.REVERSE,
                shape = RegionShape.RECT,
                left = 0.24,
                top = 0.22,
                right = 0.76,
                bottom = 0.76,
                description = "Central lettering and ornamental geometry."
            ),
            NormalizedRegion(
                id = "reverse-jawi",
                label = "Jawi / peripheral lettering zone",
                side = CoinSide.REVERSE,
                shape = RegionShape.ANNULUS,
                innerRadius = 0.48,
                outerRadius = 0.82,
                description = "Peripheral script zone; compare stroke thickness, curvature and edge definition."
            ),
            NormalizedRegion(
                id = "reverse-rim",
                label = "Reverse rim and denticles",
                side = CoinSide.REVERSE,
                shape = RegionShape.ANNULUS,
                innerRadius = 0.80,
                outerRadius = 0.97,
                description = "Peripheral rim/denticle geometry."
            )
        )

        val markers = listOf(
            DiagnosticMarker(
                id = "weight-2696",
                title = "Weight near the documented 26.96 g standard",
                category = DiagnosticCategory.WEIGHT,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.WEIGHT_TOLERANCE,
                description = "Compare the user's measured weight with the PCGS 1911-B record. Wear can remove a small amount of metal; a large deficit requires explanation.",
                sourceIds = listOf("pcgs-setregistry-weight", "ngc-1909b-counterfeit")
            ),
            DiagnosticMarker(
                id = "diameter-390",
                title = "Diameter near the documented 39 mm standard",
                category = DiagnosticCategory.EDGE,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.DIAMETER_TOLERANCE,
                description = "Compare a caliper measurement with the 39 mm dimension published for a PCGS-certified 1911-B control. Photo-derived diameter is not used because perspective and scale are unknown.",
                sourceIds = listOf("greatcollections-1747409")
            ),
            DiagnosticMarker(
                id = "date-geometry",
                title = "Date shape and placement should match documented genuine controls",
                category = DiagnosticCategory.DATE_GEOMETRY,
                side = CoinSide.OBVERSE,
                regionId = "obverse-date",
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                description = "The date is treated as its own geometry region. Note that PCGS also recognizes a 1911/00-B overdate, so an apparent date mismatch may be a legitimate variety rather than a counterfeit.",
                sourceIds = listOf("pcgs-coinfacts-1911b")
            ),
            DiagnosticMarker(
                id = "trident-mintmark-geometry",
                title = "Trident and Bombay B mintmark geometry",
                category = DiagnosticCategory.RELIEF_DETAIL,
                side = CoinSide.OBVERSE,
                regionId = "obverse-trident-mintmark",
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                description = "Compare the trident tine boundaries and tiny B placement against several exact 1911-B certified controls. The source pack also records that Bombay pieces place B in the trident's center prong.",
                sourceIds = listOf("pcgs-cert-43162343", "pcgs-auction-43594463", "apmex-1911b-spec")
            ),
            DiagnosticMarker(
                id = "ship-waves-detail",
                title = "Merchant ship and wave relief",
                category = DiagnosticCategory.RELIEF_DETAIL,
                side = CoinSide.OBVERSE,
                regionId = "obverse-ship-waves",
                scope = DiagnosticScope.SAME_TYPE_OTHER_DATE,
                automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                description = "NGC's documented 1909B counterfeit showed poor ship and wave detail. For 1911-B the image score is still computed against genuine 1911-B controls; the NGC source only supplies the type-level warning rationale.",
                sourceIds = listOf("ngc-1909b-counterfeit")
            ),
            DiagnosticMarker(
                id = "community-shield-transfer-field",
                title = "Exact-year community warning: transfer-die-like field artifacts beside the shield",
                category = DiagnosticCategory.TRANSFER_DIE,
                side = CoinSide.OBVERSE,
                regionId = "obverse-shield-field",
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.MANUAL_ONLY,
                description = "A Numista 1911 case discusses extra/imprinted-looking detail beside the shield and transfer-die-like appearance. This region is available for counterfeit-image proximity study, but the source remains community evidence and cannot trigger an automated counterfeit verdict.",
                sourceIds = listOf("numista-forum-1911-transfer")
            ),
            DiagnosticMarker(
                id = "britannia-relief",
                title = "Britannia, clothing and nearby relief should retain genuine sharpness patterns",
                category = DiagnosticCategory.RELIEF_DETAIL,
                side = CoinSide.OBVERSE,
                regionId = "obverse-relief",
                scope = DiagnosticScope.SAME_TYPE_OTHER_DATE,
                automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                description = "NGC's documented 1909B fake showed pervasive loss of detail including Britannia's clothing, ship and waves. For 1911-B this is used as a type-level caution only.",
                sourceIds = listOf("ngc-1909b-counterfeit")
            ),
            DiagnosticMarker(
                id = "reverse-lettering",
                title = "Reverse lettering stroke geometry should remain sharp and correctly proportioned",
                category = DiagnosticCategory.LETTERING,
                side = CoinSide.REVERSE,
                regionId = "reverse-jawi",
                scope = DiagnosticScope.SAME_TYPE_OTHER_DATE,
                automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                description = "NGC documented too-thick, rounded Jawi text and generally rounded lettering on a counterfeit of this British Trade Dollar type.",
                sourceIds = listOf("ngc-1909b-counterfeit")
            ),
            DiagnosticMarker(
                id = "rim-denticles-obv",
                title = "Obverse rim and denticle geometry",
                category = DiagnosticCategory.RIM_DENTICLES,
                side = CoinSide.OBVERSE,
                regionId = "obverse-rim",
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                description = "Compares the peripheral edge geometry against the attached certified genuine control. Perspective and crop quality can strongly affect this test.",
                sourceIds = listOf("pcgs-cert-43162343", "pcgs-cert-88585842")
            ),
            DiagnosticMarker(
                id = "rim-denticles-rev",
                title = "Reverse rim and denticle geometry",
                category = DiagnosticCategory.RIM_DENTICLES,
                side = CoinSide.REVERSE,
                regionId = "reverse-rim",
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                description = "Compares peripheral geometry against the attached certified genuine control.",
                sourceIds = listOf("pcgs-cert-43162343", "pcgs-cert-88585842")
            ),
            DiagnosticMarker(
                id = "surface-reference-delta",
                title = "Surface microtexture should not diverge dramatically from a comparable genuine control",
                category = DiagnosticCategory.SURFACE_TEXTURE,
                scope = DiagnosticScope.SAME_TYPE_OTHER_DATE,
                automatedTest = AutomatedTest.SURFACE_REFERENCE_DELTA,
                description = "A large texture divergence can be caused by casting, corrosion, cleaning, wear, compression or lighting. NGC documented grainy surfaces on a British Trade Dollar fake, so this test only raises a caution and never proves a counterfeit.",
                sourceIds = listOf("ngc-1909b-counterfeit")
            ),
            DiagnosticMarker(
                id = "transfer-repeating-depressions",
                title = "Look for repeating depressions/contact marks shared by multiple suspect pieces",
                category = DiagnosticCategory.TRANSFER_DIE,
                scope = DiagnosticScope.GENERAL_AUTHENTICATION,
                automatedTest = AutomatedTest.MANUAL_ONLY,
                description = "NGC explains that transferred host-coin marks can repeat across counterfeits made from the same copied die. This requires a counterfeit-reference set or several suspect pieces; a single photograph cannot establish repetition.",
                sourceIds = listOf("ngc-transfer-die-guidance", "ngc-counterfeit-resource")
            ),
            DiagnosticMarker(
                id = "color-luster-manual",
                title = "Inspect abnormal color, graininess or excessively prooflike-looking fields",
                category = DiagnosticCategory.LUSTER_COLOR,
                scope = DiagnosticScope.SAME_TYPE_OTHER_DATE,
                automatedTest = AutomatedTest.MANUAL_ONLY,
                description = "These were warning signs on NGC's documented 1909B fake. Lighting can mimic them, so the app keeps this as a manual corroborating check.",
                sourceIds = listOf("ngc-1909b-counterfeit")
            ),
            DiagnosticMarker(
                id = "overdate-variety",
                title = "Do not confuse the recognized 1911/00-B overdate with a bad date punch",
                category = DiagnosticCategory.VARIETY,
                side = CoinSide.OBVERSE,
                regionId = "obverse-date",
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.MANUAL_ONLY,
                description = "PCGS separately catalogs 1911/00-B Prid-21 OD (PCGS #207443). If the date region disagrees with a normal-date reference, compare against the overdate before drawing a counterfeit conclusion.",
                sourceIds = listOf("pcgs-pop-overdate")
            ),
            DiagnosticMarker(
                id = "thickness-source-conflict",
                title = "Thickness is corroborating evidence, not a hard threshold in this pack",
                category = DiagnosticCategory.EDGE,
                side = CoinSide.EDGE,
                scope = DiagnosticScope.SERIES_WIDE,
                automatedTest = AutomatedTest.MANUAL_ONLY,
                description = "Published references differ: Numista lists 2.7 mm for the type while an APMEX 1911-B specification lists 2.5 mm. Measure thickness carefully, but do not reject a coin from thickness alone; combine it with weight, diameter, edge, density/composition and die evidence.",
                sourceIds = listOf("numista-catalog-8472", "apmex-1911b-spec")
            ),
            DiagnosticMarker(
                id = "community-rim-edge",
                title = "Community-documented rim/edge warning: rounded rim or incomplete milling",
                category = DiagnosticCategory.EDGE,
                side = CoinSide.EDGE,
                scope = DiagnosticScope.SERIES_WIDE,
                automatedTest = AutomatedTest.MANUAL_ONLY,
                description = "A long-running Numista research thread illustrates fakes with an overly rounded rim and milling/reeding that does not extend cleanly to the edge. Because this is community evidence, use it only as a manual corroborating clue, not an automatic fail.",
                sourceIds = listOf("numista-forum-1911b-fakes", "numista-forum-1911-transfer")
            ),
            DiagnosticMarker(
                id = "community-trident-detail",
                title = "Community-documented relief warning: soft/blob-like trident or exaggerated B mintmark",
                category = DiagnosticCategory.RELIEF_DETAIL,
                side = CoinSide.OBVERSE,
                regionId = "obverse-trident-mintmark",
                scope = DiagnosticScope.SERIES_WIDE,
                automatedTest = AutomatedTest.MANUAL_ONLY,
                description = "The Numista research thread shows counterfeit examples with weak trident detail and discusses an unusually large/bold B mintmark. Treat this as a community diagnostic lead only and compare with certified genuine controls before drawing conclusions.",
                sourceIds = listOf("numista-forum-1911b-fakes", "numista-forum-1911-transfer")
            )
        )

        return ReferencePack(
            id = "gb-trade-dollar-1911b-prid21",
            displayName = "Great Britain 1911-B Trade Dollar — Prid-21",
            country = "Great Britain",
            denomination = "Trade Dollar",
            year = "1911",
            mintOrVariety = "B — Bombay; normal date Prid-21",
            catalogReferences = listOf("PCGS #207444", "Prid-21", "KM-T5", "Mars-BTD1"),
            physical = PhysicalSpecification(
                expectedWeightGrams = 26.96,
                weightToleranceGrams = 0.35,
                expectedDiameterMm = 39.0,
                diameterToleranceMm = 0.35,
                compositionNote = "Certified 1911-B auction data list a 39 mm diameter and 90% silver composition; NGC's same-type counterfeit study uses 26.96 g as the expected genuine weight and 90% silver as the genuine standard.",
                sourceIds = listOf("pcgs-setregistry-weight", "greatcollections-1747409", "numista-catalog-8472", "apmex-1911b-spec", "ngc-1909b-counterfeit")
            ),
            regions = regions,
            markers = markers,
            sources = sources,
            notes = "This pack intentionally distinguishes exact 1911-B evidence from same-type 1909B counterfeit diagnostics. A type-level warning is never promoted to an exact 1911-B die marker unless a source documents that exact variety. Third-party source images remain external; attach a locally saved certified reference image and bind it to its source id for analysis."
        )
    }
}
