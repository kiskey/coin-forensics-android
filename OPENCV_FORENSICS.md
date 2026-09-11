# OpenCV forensic engine

Coin Forensics keeps the original deterministic comparator and adds OpenCV as an independent image-evidence engine.

## Pipeline

1. Detect/normalize the coin boundary using Hough circles with a center-crop fallback.
2. Normalize local contrast using CLAHE.
3. Extract up to 2,600 ORB keypoints per image.
4. Match binary descriptors using Hamming distance and a Lowe-ratio filter.
5. Estimate a RANSAC homography from the documented reference into the user's image.
6. Warp the reference into user-image coordinates.
7. Measure Canny edge overlap, Laplacian microtexture correlation and masked luminance correlation.
8. Re-run those measurements inside every region defined by the selected reference pack.
9. Build a median OpenCV consensus from multiple documented genuine controls.
10. Compare documented counterfeit controls independently against the same user image.

A documented counterfeit only raises an automated OpenCV caution when all of these are true:

- its source kind is `COUNTERFEIT_DIAGNOSTIC`, not a community lead;
- a genuine OpenCV baseline exists for the same side;
- registration reliability is at least 0.30;
- counterfeit similarity is at least 80/100; and
- it is at least 7 points closer than the genuine-control consensus.

Not matching a known counterfeit never adds authenticity evidence.

## Verdict fusion

When reliable genuine OpenCV evidence exists, the existing evidence score contributes 72% and the OpenCV genuine-control consensus contributes 28%. A professionally documented counterfeit-proximity hit caps the combined consistency score at 74 and forces a forensic-caution interpretation. This score is deliberately described as evidence consistency, never as an authenticity probability.

## Native packaging

The app uses the official `org.opencv:opencv:4.14.0` Android AAR. GitHub Actions remains the only build/test environment. The ARM release verifier checks both APK filenames and the native-library directories inside each APK, and verifies that `libopencv_java4.so` is present only for the APK's expected ABI.
