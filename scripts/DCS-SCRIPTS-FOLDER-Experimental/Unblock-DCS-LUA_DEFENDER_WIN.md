# Unblocking Files in DCS World

The file may be blocked by **Group Policy** or **antivirus**. You can use PowerShell to unblock it.

## Using PowerShell

1. Press the **Windows key** and type `PowerShell`.
2. Right-click **Windows PowerShell** → **Run as Administrator**.
3. Enter the following command to unblock a single file:

```powershell
Unblock-File "C:\Program Files\Eagle Dynamics\DCS World\bin\lua-dxgui.dll"
````

4. Press **Enter**.

### To unblock all DLLs at once:

```powershell
Get-ChildItem "C:\Program Files\Eagle Dynamics\DCS World\bin\*.dll" | Unblock-File
```

Press **Enter**.

## Alternative: Repair DCS

If unblocking doesn’t work:

1. Open **DCS Launcher**.
2. Go to **Settings** (gear icon).
3. Click **Repair**.
4. Wait until the process is complete.

This should fix all blocked files.

