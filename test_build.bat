@echo off
echo Starting gradle... > build_log_test.txt
call gradlew.bat bundleProdRelease --warning-mode all >> build_log_test.txt 2>&1
echo Done >> build_log_test.txt
