@echo off
echo ============================================
echo  Kuran Analiz v7.1 - Backend Test
echo ============================================
cd /d "C:\Users\inanc\Desktop\Kuran i Kerim Windows Uygulama\backend"
python test_full.py
echo.
echo ============================================
echo  Bridge Protokol Testi
echo ============================================
echo {"cmd":"ping"} | python analyze_bridge.py
echo.
pause
