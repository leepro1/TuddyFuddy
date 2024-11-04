import pytorch_lightning as pl
import torch.nn as nn
import torch
import os

from transformers import ElectraModel, AutoTokenizer
from fastapi import FastAPI
from app.core.logger import setup_logger

logging = setup_logger("app")

LABELS = ['불평/불만',
          '환영/호의',
          '감동/감탄',
          '지긋지긋',
          '고마움',
          '슬픔',
          '화남/분노',
          '존경',
          '기대감',
          '우쭐댐/무시함',
          '안타까움/실망',
          '비장함',
          '의심/불신',
          '뿌듯함',
          '편안/쾌적',
          '신기함/관심',
          '아껴주는',
          '부끄러움',
          '공포/무서움',
          '절망',
          '한심함',
          '역겨움/징그러움',
          '짜증',
          '어이없음',
          '없음',
          '패배/자기혐오',
          '귀찮음',
          '힘듦/지침',
          '즐거움/신남',
          '깨달음',
          '죄책감',
          '증오/혐오',
          '흐뭇함(귀여움/예쁨)',
          '당황/난처',
          '경악',
          '부담/안_내킴',
          '서러움',
          '재미없음',
          '불쌍함/연민',
          '놀람',
          '행복',
          '불안/걱정',
          '기쁨',
          '안심/신뢰']

emotion_dict = {
    "불평/불만": "분노",
    "화남/분노": "분노",
    "지긋지긋": "분노",
    "짜증": "분노",

    "신기함/관심": "놀람",
    "당황/난처": "놀람",
    "경악": "놀람",
    "놀람": "놀람",

    "환영/호의": "행복",
    "감동/감탄": "행복",
    "고마움": "행복",
    "뿌듯함": "행복",
    "편안/쾌적": "행복",
    "즐거움/신남": "행복",
    "흐뭇함(귀여움/예쁨)": "행복",
    "행복": "행복",
    "기쁨": "행복",
    "기대감": "행복",

    "공포/무서움": "공포",
    "불안/걱정": "공포",

    "슬픔": "슬픔",
    "안타까움/실망": "슬픔",
    "절망": "슬픔",
    "패배/자기혐오": "슬픔",
    "힘듦/지침": "슬픔",
    "서러움": "슬픔",
    "불쌍함/연민": "슬픔",

    "역겨움/징그러움": "기타",
    "한심함": "기타",
    "증오/혐오": "기타",
    "재미없음": "기타",
    "존경": "기타",
    "우쭐댐/무시함": "기타",
    "비장함": "기타",
    "아껴주는": "기타",
    "부끄러움": "기타",
    "어이없음": "기타",
    "없음": "기타",
    "귀찮음": "기타",
    "깨달음": "기타",
    "죄책감": "기타",
    "부담/안_내킴": "기타",
    "안심/신뢰": "기타",
    "의심/불신": "기타",
}

device = "cuda" if torch.cuda.is_available() else "cpu"


class KOTEtagger(pl.LightningModule):
    def __init__(self):
        super().__init__()
        self.electra = ElectraModel.from_pretrained("beomi/KcELECTRA-base", revision='v2021').to(device)
        self.tokenizer = AutoTokenizer.from_pretrained("beomi/KcELECTRA-base", revision='v2021')
        self.classifier = nn.Linear(self.electra.config.hidden_size, 44).to(device)

    def forward(self, text: str):
        encoding = self.tokenizer.encode_plus(
            text,
            add_special_tokens=True,
            max_length=512,
            return_token_type_ids=False,
            padding="max_length",
            return_attention_mask=True,
            return_tensors='pt',
        ).to(device)
        output = self.electra(encoding["input_ids"], attention_mask=encoding["attention_mask"])
        output = output.last_hidden_state[:, 0, :]
        output = self.classifier(output)
        output = torch.sigmoid(output)
        torch.cuda.empty_cache()

        return output


def find_and_match(preds):
    maxval = -1
    maxidx = -1
    for i in range(len(preds)):
        if preds[i] > maxval:
            maxval = preds[i]
            maxidx = i

    # 감정 대분류 매칭
    return LABELS[maxidx]


# 현재 파일의 디렉토리 경로를 가져옵니다
current_dir = os.path.dirname(os.path.abspath(__file__))

# 모델 파일 경로를 만듭니다
model_path = os.path.join(current_dir, "kote_pytorch_lightning.bin")

trained_model = KOTEtagger()

state_dict = torch.load(
    model_path,
    weights_only=True,  # Address the security warning
    map_location=device
)
trained_model.load_state_dict(state_dict, strict=False)
logging.info("--------------------     Model KOTE loaded.     --------------------")


async def get_emotion(message: str):
    preds = trained_model(
        message
    )[0]

    result = find_and_match(preds)

    return emotion_dict[result]