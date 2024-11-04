from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    EUREKA_SERVER_URL: str = "http://discovery-service:8761/eureka"
    SERVICE_NAME: str = "chat-service"
    SERVICE_PORT: int = 8000
    GPT_KEY: str = "."
    
    class Config:
        env_file = ".env"

settings = Settings()