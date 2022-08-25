import { IonicNativePlugin } from '@ionic-native/core';

export interface LuxandConfig {
    /** Chave de acesso Luxand */
    licence: string;

    /** Nome do banco de dados interno que o tracker deve usar */
    dbname: string;

    /** Número de repetições para o processo de comparação e registro */
    loginTryCount: number;
}

export interface OMLFacialData {
    /** Mensagem de status (SUCCESS ou FAIL) */
    status: STATUS_FSDK;

    /** Mensagem retornada pelo plugin */
    message: string;

    /** Nome exclusivo gerado e associado a uma face ao se registrar (o identificador é uma chave alfanumérica) */
    name: string;

    /** Tracker ID - exclusivo associa-se a uma face no banco de dados interno */
    id: number;

    /** Template da face (formato BASE64) */
    template: string;
}

export enum STATUS_FSDK {
    SUCCESS = "SUCCESS",
    FAIL = "FAIL"
}

/**
 * @name Luxand
 * @description
 * Este plugin permite integrar o Luxand FaceSDK no projeto
 *
 * @interfaces
 * OMLFacialData
 * LuxandConfig
 */
export declare class LuxandOriginal extends IonicNativePlugin {
    /**
     * Inicializa o FaceSDK de acordo com a chave de acesso
     * @param config Objeto de configuração LuxandConfig a ser usado para iniciar o SDK
     * @return {Promise<any>} Retorna uma Promise que resolve se o Luxand FaceSDK for inicializado com sucesso
     */
    init(config: LuxandConfig): Promise<any>;

    /**
     * Tenta registrar um rosto
     * @param params Tempo limite
     * @param template Template do rosto obtido do servidor (caso houver)
     * @return {Promise<OMLFacialData>} Retorna uma Promise que resolve se um rosto foi detectado e reconhecido como um novo rosto
     */
    register(params: { timeout: number }, template: string): Promise<OMLFacialData>;

    /**
     * Compara se o template da rosto detectada é igual ao template original
     * @param params Tempo limite
     * @param template Template do rosto obtido do servidor
     * @return {Promise<OMLFacialData>} Retorna uma Promise que resolve se o template do rosto detectado é igual ao template original
     */
    compare(params: { timeout: number }, template: string): Promise<OMLFacialData>;

    /**
     * Realiza a limpeza na base local por ID
     * @param id
     * @return {Promise<any>} Retorna uma Promise que resolve se o rosto foi excluído
     */
    clear(id: number): Promise<any>;

    /**
     * Realiza a limpeza de todos os dados na base interna
     * @return {Promise<any>} Retorna uma Promise que resolve se toda base foi excluída
     */
    clearMemory(): Promise<any>;
}

export declare const Luxand: LuxandOriginal;