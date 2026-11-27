from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+asyncpg://docscan:docscan@db:5432/docscan"
    jwt_secret: str = "change-me"
    jwt_algorithm: str = "HS256"
    jwt_expire_days: int = 365
    api_public_host: str = "https://api.docscan.cam"
    apple_bundle_id: str = "cam.docscan"
    google_play_package_name: str = "cam.docscan"

    # Billing verification
    billing_stub_mode: bool = True
    apple_issuer_id: str = ""
    apple_key_id: str = ""
    apple_private_key: str = ""
    apple_use_sandbox: bool = False
    google_play_service_account_json: str = ""


settings = Settings()
