@echo off
pushd "%~dp0" 
bin\java.exe --module-path lib -m xliffFilters/com.maxprograms.stats.RepetitionAnalysis %* 