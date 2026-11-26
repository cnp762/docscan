from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+asyncpg://docscan:docscan@db:5432/docscan"
    jwt_secret: str = "change-me"
    api_public_host: str = "https://api.docscan.cam"
    apple_bundle_id: str = "cam.docscan"
    google_play_package_name: str = "cam.docscan"


settings = Settings()
