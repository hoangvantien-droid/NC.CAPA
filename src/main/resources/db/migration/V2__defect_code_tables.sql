CREATE TABLE nhom_loi (
  nhom_loi_id VARCHAR(8)   PRIMARY KEY,
  ten_nhom    VARCHAR(255) NOT NULL,
  theo_nguon  VARCHAR(64)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ma_loi (
  ma_loi_id                 VARCHAR(16) PRIMARY KEY,
  nhom_loi_id               VARCHAR(8)  NOT NULL,
  ten_ma_loi                VARCHAR(255) NOT NULL,
  muc_nghiem_trong_default  VARCHAR(16)  NOT NULL,  -- NGHIEM_TRONG|NANG|NHE
  moi_nguy_default          VARCHAR(16)  NOT NULL,  -- VAT_LY|HOA_HOC|VI_SINH|DI_UNG|CAM_QUAN|KHONG
  bat_buoc_khoanh_vung      TINYINT(1)   NOT NULL DEFAULT 0,
  nhom_n_kiem_chung         VARCHAR(24)  NOT NULL,  -- SX_TP_NVL | HETHONG_TBDO_NCC_MT
  nguoi_so_huu              VARCHAR(64)  NOT NULL DEFAULT 'QA_TAP_DOAN',
  hieu_luc_tu               DATE NOT NULL,
  hieu_luc_den              DATE NULL,
  CONSTRAINT fk_maloi_nhom FOREIGN KEY (nhom_loi_id) REFERENCES nhom_loi(nhom_loi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
