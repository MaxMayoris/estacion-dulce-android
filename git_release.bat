@echo off
echo Running git add... > git_log.txt
git add . >> git_log.txt 2>&1
echo Running git commit... >> git_log.txt
git commit -m "Add in-stock field for recipes and improve UI layout" >> git_log.txt 2>&1
echo Running git push... >> git_log.txt
git push >> git_log.txt 2>&1
echo DONE >> git_log.txt
