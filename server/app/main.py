from fastapi import FastAPI

from app.routers import billing, devices, health, quota

app = FastAPI(title="DocScan API", version="0.1.0")

app.include_router(health.router)
app.include_router(devices.router)
app.include_router(quota.router)
app.include_router(billing.router)
