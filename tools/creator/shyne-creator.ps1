param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CreatorArgs
)

# Keep the Windows entry point tiny so the Python CLI remains the single source of truth.
python "$PSScriptRoot\shyne_creator.py" @CreatorArgs
exit $LASTEXITCODE
